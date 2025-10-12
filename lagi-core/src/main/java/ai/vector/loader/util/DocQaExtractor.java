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
import ai.vector.QaChunkCallback;
import ai.vector.VectorStoreConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
public class DocQaExtractor {
    private static final Backend text2qaBackend = ContextLoader.configuration.getFunctions().getText2qa();
    private static final int THREAD_COUNT = 20;

    private static final String PROMPT_TEMPLATE = "请将提供的长文本拆分成多个较小的段落，以便更好地管理和处理，同时保持内容的连贯性和上下文完整性。\n" +
            "将拆分后的文本转化为多个“问答对”，并以指定的 JSON 格式输出。\n" +
            "\n" +
            "**严格要求：**\n" +
            "\n" +
            "1. 仅输出 JSON 格式内容，不要附加任何解释、说明或多余文字。\n" +
            "2. 输出内容必须完整、符合 JSON 语法且可解析。\n" +
            "3. 拆分时应尽可能保留原文的语义与细节。\n" +
            "4. 每个“instruction”字段应是一个有意义的自然语言问题，不能只是词语或短语。\n" +
            "5. “output”字段应是对应问题的回答内容。\n" +
            "\n" +
            "**输出格式：**\n" +
            "\n" +
            "```json\n" +
            "[\n" +
            "  {\n" +
            "    \"instruction\": \"问题\",\n" +
            "    \"output\": \"答案\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"instruction\": \"问题\",\n" +
            "    \"output\": \"答案\"\n" +
            "  }\n" +
            "]\n" +
            "```\n" +
            "\n" +
            "**待处理内容：**\n%s";

    private final static CompletionsService completionService = new CompletionsService();

    //并行处理
    public static List<List<FileChunkResponse.Document>> parseText(List<List<FileChunkResponse.Document>> docs) throws JsonProcessingException {
        if (text2qaBackend == null || !text2qaBackend.getEnable()) {
            return docs;
        }

        List<List<FileChunkResponse.Document>> result = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
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
                                log.warn("Failed to extract JSON from LLM response for document {}. Using original document.", finalI);
                                document.setOrder(finalI + 1);
                                qaDocs.add(document);
                                return;
                            }
                            ObjectMapper objectMapper = new ObjectMapper();
                            try {
                                List<Map<String, String>> dataList = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                                });
                                for (Map<String, String> map : dataList) {
                                    String instruction = map.get("instruction");
                                    if (instruction != null && !instruction.trim().isEmpty()) {
                                        FileChunkResponse.Document doc = new FileChunkResponse.Document();
                                        doc.setText(instruction);
                                        doc.setSource(VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_LLM);
                                        doc.setOrder(null);
                                        doc.setReferenceDocumentId(Integer.valueOf(finalI + 1).toString());
                                        qaDocs.add(doc);
                                    }
                                }
                                document.setOrder(finalI + 1); // 设置文档的顺序
                                qaDocs.add(document);
                            } catch (JsonProcessingException e) {
                                log.error("JSON parsing error for document {}: {}", finalI, e.getMessage());
                                document.setOrder(finalI + 1);
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
            return result;
        } catch (Exception e) {
            log.error("Error parsing text", e);
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
            QaChunkCallback onChunkReady, // ← 新增：分片完成回调
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
                        List<FileChunkResponse.Document>[] buckets = (List<FileChunkResponse.Document>[]) new List[group.size()];
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
                                                om.readValue(json, new TypeReference<List<Map<String, String>>>() {
                                                });
                                        for (Map<String, String> map : dataList) {
                                            String instruction = map.get("instruction");
                                            if (instruction != null && !instruction.trim().isEmpty()) {
                                                FileChunkResponse.Document doc = new FileChunkResponse.Document();
                                                doc.setText(instruction);
                                                doc.setSource(VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_LLM);
                                                doc.setOrder(null);
                                                doc.setReferenceDocumentId(String.valueOf(idx + 1));
                                                block.add(doc);
                                            }
                                        }
                                    } else {
                                        log.warn("Failed to extract JSON from LLM response for document {}. Using original document.", idx);
                                    }
                                } catch (Exception e) {
                                    log.error("JSON parsing error for document {}: {}", idx, e.getMessage());
                                }
                                original.setOrder(idx + 1);
                                block.add(original);

                                buckets[idx] = block; // 写到自己的槽位
                                // 每个分片完成后立刻回调
                                if (onChunkReady != null) {
                                    try {
                                        onChunkReady.onChunk(groupIndex, idx, block);
                                    } catch (Exception ignored) {
                                    }
                                }
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
