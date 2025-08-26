package ai.vector.loader.util;

import ai.common.pojo.Backend;
import ai.common.pojo.FileChunkResponse;
import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.JsonExtractor;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorStoreConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DocQaExtractor {
    private static final Backend text2qaBackend = ContextLoader.configuration.getFunctions().getText2qa();

    private static final String PROMPT_TEMPLATE = "请将长文本分割成更易于管理和处理的较小段落，以适应模型的输入限制，同时保持文本的连贯性和上下文信息。" +
            "将提供的需要拆分的内容拆分成多个问答对，以指定格式生成一个 JSON 文件，" +
            "且仅返回符合要求的 JSON 内容，不要附带其他解释或回答。\n" +
            "注意:\n" +
            "1.仅返回指定格式的json内容，不用额外回答其它任何内容。\n" +
            "2.返回的文件内容要完整。\n" +
            "3.要尽可能保留原文内容的完整性。\n" +
            "4.返回格式为:\n" +
            "[\n" +
            "    {\n" +
            "      \"instruction\": \"问题\",\n" +
            "      \"output\": \"答案\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"instruction\": \"问题\",\n" +
            "      \"output\": \"答案\",\n" +
            "    }\n" +
            "]\n" +
            "需要拆分的内容:\n%s";

    private final static CompletionsService completionService = new CompletionsService();

    /**
     * 串行处理，已弃用--
     * @param docs
     * @return
     * @throws JsonProcessingException
     */
    public static List<List<FileChunkResponse.Document>> parseText1(List<List<FileChunkResponse.Document>> docs) throws JsonProcessingException {
        if (text2qaBackend == null || !text2qaBackend.getEnable()) {
            return docs;
        }
        List<List<FileChunkResponse.Document>> result = new ArrayList<>();
        for (List<FileChunkResponse.Document> documentList : docs) {
            List<FileChunkResponse.Document> qaDocs = new ArrayList<>();
            for (int i = 0; i < documentList.size(); i++) {
                FileChunkResponse.Document document = documentList.get(i);
                String prompt = String.format(PROMPT_TEMPLATE, document.getText());
                String json = extract(prompt);
                if (json == null) {
                    throw new RuntimeException("Extracted JSON is null, please check the prompt or backend configuration.");
                }
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, String>> dataList = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                });
                for (Map<String, String> map : dataList) {
                    String instruction = map.get("instruction");
                    FileChunkResponse.Document doc = new FileChunkResponse.Document();
                    doc.setText(instruction);
                    doc.setSource(VectorStoreConstant.FILE_CHUNK_SOURCE_LLM);
                    qaDocs.add(doc);
                }
                qaDocs.add(document);
            }
            result.add(qaDocs);
        }
        return result;
    }
//并行处理
    public static List<List<FileChunkResponse.Document>> parseText(List<List<FileChunkResponse.Document>> docs) throws JsonProcessingException {
        if (text2qaBackend == null || !text2qaBackend.getEnable()) {
            return docs;
        }

        long startTimeMillis = System.currentTimeMillis();
        List<List<FileChunkResponse.Document>> result = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(20); // 控制线程池大小
        List<CompletableFuture<List<FileChunkResponse.Document>>> futures = new ArrayList<>();

        try {
            for (List<FileChunkResponse.Document> documentList : docs) {
                CompletableFuture<List<FileChunkResponse.Document>> future = CompletableFuture.supplyAsync(() -> {
                    List<FileChunkResponse.Document> qaDocs = new ArrayList<>();
                    List<CompletableFuture<Void>> innerFutures = new ArrayList<>();

                    for (int i = 0; i < documentList.size(); i++) {
                        FileChunkResponse.Document document = documentList.get(i);
                        int finalI = i;
                        innerFutures.add(CompletableFuture.runAsync(() -> {
                            String prompt = String.format(PROMPT_TEMPLATE, document.getText());
                            String json = extract(prompt);
                            if (json == null) {
                                throw new RuntimeException("Extracted JSON is null, please check the prompt or backend configuration.");
                            }
                            ObjectMapper objectMapper = new ObjectMapper();
                            try {
                                List<Map<String, String>> dataList = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
                                for (Map<String, String> map : dataList) {
                                    String instruction = map.get("instruction");
                                    FileChunkResponse.Document doc = new FileChunkResponse.Document();
                                    doc.setText(instruction);
                                    doc.setSource(VectorStoreConstant.FILE_CHUNK_SOURCE_LLM);
                                    doc.setOrder(null);
                                    doc.setReferenceDocumentId(Integer.valueOf(finalI + 1).toString());
                                    qaDocs.add(doc);
                                }
                                document.setOrder(finalI + 1); // 设置文档的顺序
                                qaDocs.add(document);
                            } catch (JsonProcessingException e) {
                                System.out.println(document + " JSON解析错误：" + json);
                                qaDocs.add(document);
                            }
                        }, executorService));
                    }

                    // 等待所有子任务完成
                    CompletableFuture.allOf(innerFutures.toArray(new CompletableFuture[0])).join();
                    return qaDocs;
                }, executorService);

                futures.add(future);
            }

            // 等待所有任务完成并收集结果
            for (CompletableFuture<List<FileChunkResponse.Document>> future : futures) {
                result.add(future.get());
            }

            long endTimeMillis = System.currentTimeMillis();
            long durationMillis = endTimeMillis - startTimeMillis;
            System.out.println("上传文件总耗时（毫秒）： " + durationMillis);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return docs;
        } finally {
            executorService.shutdown();  // 关闭线程池
        }
    }

    public static String extract(String prompt) {
        String model = text2qaBackend.getModel();
        ChatCompletionRequest chatCompletionRequest = getCompletionRequest(model, prompt);
        ChatCompletionResult chatCompletionResult = completionService.completions(chatCompletionRequest);
        String content = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
        return JsonExtractor.extractFirstJsonArray(content);
    }

    private static ChatCompletionRequest getCompletionRequest(String model, String prompt) {
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.6);
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setMax_tokens(4096);
        chatCompletionRequest.setModel(model);
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message = new ChatMessage();
        message.setRole(LagiGlobal.LLM_ROLE_USER);
        message.setContent(prompt);
        messages.add(message);
        chatCompletionRequest.setMessages(messages);
        return chatCompletionRequest;
    }

    public static CompletableFuture<List<List<FileChunkResponse.Document>>> parseTextAsync(
            List<List<FileChunkResponse.Document>> docs,
            ExecutorService executor,
            BiConsumer<Integer, List<FileChunkResponse.Document>> onGroupReady
    ) {
        if (text2qaBackend == null || !text2qaBackend.getEnable()) {
            // 兼容：不开启时直接同步包一层 future
            return CompletableFuture.completedFuture(docs);
        }

        List<CompletableFuture<List<FileChunkResponse.Document>>> groupFutures = new ArrayList<>();

        for (int gi = 0; gi < docs.size(); gi++) {
            final int groupIndex = gi;
            final List<FileChunkResponse.Document> group = docs.get(gi);

            CompletableFuture<List<FileChunkResponse.Document>> gf =
                    CompletableFuture.supplyAsync(() -> {
                        // —— 组内并发，但“分桶合并”以保证顺序 —— //
                        @SuppressWarnings("unchecked")
                        List<FileChunkResponse.Document>[] buckets =
                                (List<FileChunkResponse.Document>[]) new List[group.size()];

                        List<CompletableFuture<Void>> inner = new ArrayList<>(group.size());

                        for (int i = 0; i < group.size(); i++) {
                            final int idx = i;
                            final FileChunkResponse.Document original = group.get(i);

                            inner.add(CompletableFuture.runAsync(() -> {
                                String prompt = String.format(PROMPT_TEMPLATE, original.getText());
                                String json = extract(prompt);
                                List<FileChunkResponse.Document> block = new ArrayList<>();
                                try {
                                    if (json != null) {
                                        ObjectMapper om = new ObjectMapper();
                                        List<Map<String, String>> dataList =
                                                om.readValue(json, new TypeReference<List<Map<String, String>>>() {});
                                        for (Map<String, String> map : dataList) {
                                            FileChunkResponse.Document doc = new FileChunkResponse.Document();
                                            doc.setText(map.get("instruction"));
                                            doc.setSource(VectorStoreConstant.FILE_CHUNK_SOURCE_LLM);
                                            doc.setOrder(null);
                                            doc.setReferenceDocumentId(String.valueOf(idx + 1));
                                            block.add(doc);
                                        }
                                    }
                                } catch (Exception e) {
                                    // 解析失败就只回原文
                                }
                                original.setOrder(idx + 1);
                                block.add(original);

                                buckets[idx] = block; // 写到自己的槽位
                            }, executor));
                        }

                        CompletableFuture.allOf(inner.toArray(new CompletableFuture[0])).join();

                        // 按索引顺序合并
                        List<FileChunkResponse.Document> qaDocs = new ArrayList<>();
                        for (int i = 0; i < buckets.length; i++) {
                            if (buckets[i] != null) qaDocs.addAll(buckets[i]);
                        }
                        return qaDocs;
                    }, executor).whenComplete((qaDocs, ex) -> {
                        if (ex == null && onGroupReady != null) {
                            onGroupReady.accept(groupIndex, qaDocs); // —— 该组完成立刻回调 —— //
                        }
                    });

            groupFutures.add(gf);
        }

        // 等全部组完成后返回总结果（不影响“每组先回调”的流式体验）
        return CompletableFuture
                .allOf(groupFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> groupFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }
}
