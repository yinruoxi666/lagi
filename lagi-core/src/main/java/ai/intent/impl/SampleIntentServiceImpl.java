package ai.intent.impl;


import ai.common.pojo.EmbeddingConfig;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.VectorStoreConfig;
import ai.common.utils.ThreadPoolManager;
import ai.config.ContextLoader;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.intent.IntentService;
import ai.intent.enums.IntentStatusEnum;
import ai.intent.enums.IntentTypeEnum;
import ai.intent.pojo.IntentResult;
import ai.medusa.utils.PromptCacheTrigger;
import ai.openai.pojo.ChatCompletionRequest;
import ai.utils.ContinueWordUtil;
import ai.utils.EmbeddingSimilarityCalculator;
import ai.utils.StoppingWordUtil;
import ai.utils.StrFilterUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorStoreService;
import ai.vector.pojo.MultiQueryCondition;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.thirdparty.com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;


@Slf4j
public class SampleIntentServiceImpl implements IntentService {
    private static final String punctuations = "[\\.,;!\\?，。；！？]";
    private static final ExecutorService executor;

    static {
        ThreadPoolManager.registerExecutor("vector_intent");
        executor = ThreadPoolManager.getExecutor("vector_intent");
    }

    private List<String> splitByPunctuation(String content) {
        String[] split = content.split(punctuations);
        return Arrays.stream(split).filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }

    private IntentTypeEnum detectType(ChatCompletionRequest chatCompletionRequest) {
        String lastMessage = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        List<String> segments = splitByPunctuation(lastMessage);
        IntentTypeEnum[] enums = IntentTypeEnum.values();
        for (IntentTypeEnum e : enums) {
            if (e.matches(lastMessage, segments)) {
                return e;
            }
        }
        return IntentTypeEnum.TEXT;
    }

    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest, Map<String, Object> where) {
        IntentTypeEnum intentTypeEnum = detectType(chatCompletionRequest);
        IntentResult intentResult = new IntentResult();
        intentResult.setType(intentTypeEnum.getName());
        if (intentTypeEnum != IntentTypeEnum.TEXT
                || chatCompletionRequest.getMax_tokens() <= 0) {
            return intentResult;
        }
        intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
        List<Integer> res = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        if (res.size() == 1) {
            return intentResult;
        }
        String lastQ = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        boolean isStop = StoppingWordUtil.containsStoppingWorlds(lastQ);
        if (isStop) {
            return intentResult;
        }
        Integer lIndex = res.get(0);
        boolean isContinue = ContinueWordUtil.containsStoppingWorlds(lastQ);
        if (isContinue) {
            intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
            intentResult.setContinuedIndex(lIndex);
            return intentResult;
        }
        setIntentByVector(chatCompletionRequest, lIndex, lastQ, intentResult, where);
        return intentResult;
    }


    public IntentResult detectSegmentationBoundary(ChatCompletionRequest chatCompletionRequest) {
        IntentTypeEnum intentTypeEnum = detectType(chatCompletionRequest);
        IntentResult intentResult = new IntentResult();
        intentResult.setType(intentTypeEnum.getName());
        intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
        List<Integer> res = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        if (res.size() == 1) {
            intentResult.setContinuedIndex(0);
            return intentResult;
        }
        String lastQ = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        boolean isStop = StoppingWordUtil.containsStoppingWorlds(lastQ);
        if (isStop) {
            intentResult.setContinuedIndex(chatCompletionRequest.getMessages().size()-1);
            return intentResult;
        }
        Integer lIndex = res.get(0);
        boolean isContinue = ContinueWordUtil.containsStoppingWorlds(lastQ);
        if (isContinue) {
            intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
            intentResult.setContinuedIndex(lIndex);
            return intentResult;
        }
        if(ContextLoader.configuration != null
                && ContextLoader.configuration.getFunctions().getEmbedding() != null
                && !ContextLoader.configuration.getFunctions().getEmbedding().isEmpty()) {
            List<EmbeddingConfig> embedding = ContextLoader.configuration.getFunctions().getEmbedding();
            EmbeddingConfig config = embedding.get(0);
            Embeddings embeddings = EmbeddingFactory.getEmbedding(config);
            String q1 = chatCompletionRequest.getMessages().get(res.get(0)).getContent();
            String q2 = chatCompletionRequest.getMessages().get(res.get(1)).getContent();
            List<List<Float>> embeddingDataList = embeddings.createEmbedding(Lists.newArrayList(q1+"\n"+q2, q1));
            double similarity = EmbeddingSimilarityCalculator.calculateCosineSimilarity(embeddingDataList.get(0), embeddingDataList.get(1));
            if(similarity > 0.91) {
                intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
                intentResult.setContinuedIndex(res.get(0));
            } else {
                intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
                intentResult.setContinuedIndex(res.get(1));
            }
        }
        return intentResult;
    }

    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest) {
        return detectIntent(chatCompletionRequest, null);
    }

    private static void setIntentByVector(ChatCompletionRequest chatCompletionRequest, Integer lIndex, String lastQ, IntentResult intentResult, Map<String, Object> where) {
        VectorStoreService vectorStoreService = new VectorStoreService();
        String lQ = chatCompletionRequest.getMessages().get(lIndex).getContent();
        String complexQ = lQ + lastQ;
        lastQ = StrFilterUtil.filterPunctuations(lastQ);
        complexQ = StrFilterUtil.filterPunctuations(complexQ);
        String finalLastQ = lastQ;
        String finalComplexQ = complexQ;

        VectorStoreConfig vectorStoreConfig = vectorStoreService.getVectorStoreConfig();
        List<String> texts = new ArrayList<>();
        texts.add(finalLastQ);
        texts.add(finalComplexQ);
        MultiQueryCondition multiQueryCondition = MultiQueryCondition.builder()
                .category(chatCompletionRequest.getCategory())
                .texts(texts)
                .where(where)
                .n(vectorStoreConfig.getSimilarityTopK())
                .build();
        
        List<List<IndexSearchData>> indexSearchDataList = vectorStoreService.search(multiQueryCondition);

        try {
            List<IndexSearchData> l = indexSearchDataList.get(0);
            List<IndexSearchData> c = indexSearchDataList.get(1);
            boolean vectorContinue = false;
            if (!l.isEmpty() && !c.isEmpty()) {
                if (c.get(0).getDistance() < l.get(0).getDistance()) {
                    vectorContinue = true;
                }
            } else if (!l.isEmpty()) {
                vectorContinue = true;
            }
            if (vectorContinue) {
                intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
                intentResult.setContinuedIndex(lIndex);
                intentResult.setIndexSearchDataList(c);
            } else {
                intentResult.setIndexSearchDataList(l);
            }
        } catch (Exception e) {
            log.error("detectIntent error", e);
        }
    }
}
