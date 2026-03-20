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
import ai.openai.pojo.ChatMessage;
import ai.common.utils.LRUCache;
import ai.utils.*;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
public class SampleIntentServiceImpl implements IntentService {
    private static final String punctuations = "[\\.,;!\\?，。；！？]";
    private static final ExecutorService executor;
    private static String KEY = "embedding_disabled";
    private static LRUCache<String, Boolean> cache = new LRUCache<>(1, 1, TimeUnit.HOURS);
    private static final LRUCache<List<ChatMessage>, Integer> BOUDARY_CACHE = new LRUCache<>(1, 7, TimeUnit.DAYS);

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


    public List<ChatMessage> detectSegmentationBoundary(ChatCompletionRequest chatCompletionRequest) {
        List<ChatMessage> chatMessages = chatCompletionRequest.getMessages();
        List<ChatMessage> historyMessages = ChatCompletionUtil.getHistoryMessages(chatMessages);
        Integer firstUserIndex = ChatCompletionUtil.getFirstUserIndex(chatMessages);
        if(firstUserIndex == null) {
            return chatMessages;
        }
        if(historyMessages.isEmpty()) {
            return chatMessages;
        }
        // only qa user can be key
        Integer lastQAUserIndex = ChatCompletionUtil.getLastQAUserIndex(chatMessages);
        if(lastQAUserIndex == null) {
            // system user toolCall tool
            return chatMessages;
        }
        //
        Integer lastAssistantIndex = ChatCompletionUtil.findLastAssistantIndex(chatMessages);
        if(lastAssistantIndex == null) {
            return chatMessages;
        }
        List<ChatMessage> key = chatMessages.subList(firstUserIndex, lastAssistantIndex);
        Integer conversationStartIndex = BOUDARY_CACHE.get(key);
        List<Integer> bd = PromptCacheTrigger.theFinalRoundOfConversation(chatMessages);
        if(conversationStartIndex != null) {
            if(!bd.isEmpty()) {
                Integer bStartIndex = bd.get(0);
                if(bStartIndex > conversationStartIndex) {
                    conversationStartIndex = bStartIndex;
                }
            }
        } else {
            if(bd.isEmpty()) {
                return chatMessages;
            }
            conversationStartIndex = bd.get(0);
        }

        List<ChatMessage> incrementMessages = ChatCompletionUtil.getIncrementMessages(chatMessages);
        List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
        // user message
        List<ChatMessage> inputMessages = new ArrayList<>(systemMessages);
        List<ChatMessage> currentKey = chatMessages.stream()
                .filter(chatMessage -> !chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM))
                .collect(Collectors.toList());
        if(incrementMessages.get(0).getRole().equals(LagiGlobal.LLM_ROLE_USER)) {
            boolean embeddingSimilar = isEmbeddingSimilar(chatCompletionRequest, conversationStartIndex, incrementMessages.get(0));
            if(!embeddingSimilar) {
                inputMessages.addAll(incrementMessages);
                BOUDARY_CACHE.put(currentKey, chatMessages.size() - 1);
                BOUDARY_CACHE.remove(key);
                return inputMessages;
            } else {
                List<ChatMessage> subList = chatMessages.subList(conversationStartIndex, chatMessages.size());
                inputMessages.addAll(subList);
                BOUDARY_CACHE.put(currentKey, conversationStartIndex);
                BOUDARY_CACHE.remove(key);
                return inputMessages;
            }
        }
        // tool message
        List<ChatMessage> subList = chatMessages.subList(conversationStartIndex, chatMessages.size());
        inputMessages.addAll(subList);
        BOUDARY_CACHE.put(currentKey, conversationStartIndex);
        BOUDARY_CACHE.remove(key);
        return inputMessages;
    }

    private boolean isEmbeddingSimilar(ChatCompletionRequest chatCompletionRequest, Integer boundary, ChatMessage userMessage) {
        if(ContextLoader.configuration != null
                && ContextLoader.configuration.getFunctions().getEmbedding() != null
                && !ContextLoader.configuration.getFunctions().getEmbedding().isEmpty()
                && !Boolean.TRUE.equals(cache.get(KEY))) {
            try {
                List<EmbeddingConfig> embedding = ContextLoader.configuration.getFunctions().getEmbedding();
                EmbeddingConfig config = embedding.get(0);
                Embeddings embeddings = EmbeddingFactory.getEmbedding(config);
                String q1 = chatCompletionRequest.getMessages().get(boundary).getContent();
                String q2 = userMessage.getContent();
                q1 = q1.substring(0, Math.min(q1.length(), 1000));
                q2 = q2.substring(0, Math.min(q2.length(), 1000));
                List<List<Float>> embeddingDataList = embeddings.createEmbedding(Lists.newArrayList(q1+"\n"+q2, q1));
                double similarity = EmbeddingSimilarityCalculator.calculateCosineSimilarity(embeddingDataList.get(0), embeddingDataList.get(1));
                if(similarity <= 0.91) {
                    return false;
                }
            } catch (Exception e) {
                cache.put(KEY, true);
                log.error("embedding error", e);
            }
        }
        return true;
    }


    public List<Integer> theFinalRoundOfConversation(List<ChatMessage> chatMessages) {
        return PromptCacheTrigger.theFinalRoundOfConversation(chatMessages);
    }

    public boolean isContinue(List<ChatMessage> chatMessages, ChatMessage lastUserMessage) {
        String lastQ = lastUserMessage.getContent();
        boolean isStop = StoppingWordUtil.containsStoppingWorlds(lastQ);
        if(isStop) {
            return false;
        }
        boolean isContinueWord = ContinueWordUtil.containsStoppingWorlds(lastQ);
        if(isContinueWord) {
            return true;
        }
        if(chatMessages.isEmpty()) {
            return false;
        }
        String preQ = chatMessages.get(0).getContent();
        if(ContextLoader.configuration != null
                && ContextLoader.configuration.getFunctions().getEmbedding() != null
                && !ContextLoader.configuration.getFunctions().getEmbedding().isEmpty()) {
            List<EmbeddingConfig> embedding = ContextLoader.configuration.getFunctions().getEmbedding();
            EmbeddingConfig config = embedding.get(0);
            Embeddings embeddings = EmbeddingFactory.getEmbedding(config);
            String q1 = preQ;
            String q2 = lastQ;
            q1 = q1.substring(0, Math.min(q1.length(), 1000));
            q2 = q2.substring(0, Math.min(q2.length(), 1000));
            try {
                List<List<Float>> embeddingDataList = embeddings.createEmbedding(Lists.newArrayList(q1+"\n"+q2, q1));
                double similarity = EmbeddingSimilarityCalculator.calculateCosineSimilarity(embeddingDataList.get(0), embeddingDataList.get(1));
                return similarity > 0.91;
            } catch (Exception e) {
                log.warn("isContinue: embedding call failed ({}), treating as new conversation", e.getMessage());
                return false;
            }
        }
        return false;
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
