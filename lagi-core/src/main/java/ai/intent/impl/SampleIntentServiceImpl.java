package ai.intent.impl;


import ai.common.pojo.IndexSearchData;
import ai.common.utils.ThreadPoolManager;
import ai.intent.IntentService;
import ai.intent.enums.IntentStatusEnum;
import ai.intent.enums.IntentTypeEnum;
import ai.intent.pojo.IntentResult;
import ai.medusa.utils.PromptCacheTrigger;
import ai.openai.pojo.ChatCompletionRequest;
import ai.utils.ContinueWordUtil;
import ai.utils.StoppingWordUtil;
import ai.utils.StrFilterUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorStoreService;
import ai.vector.diagnostics.VectorSearchPerformanceContext;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;


@Slf4j
public class SampleIntentServiceImpl implements IntentService {
    private static final String punctuations = "[\\.,;!\\?，。；！？]";
    private static final ThreadPoolExecutor executor;

    static {
        ThreadPoolManager.registerExecutor("vector_intent",
                VectorSearchPerformanceContext.diagnosticThreadFactory("vector-intent"));
        executor = (ThreadPoolExecutor) ThreadPoolManager.getExecutor("vector_intent");
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
        VectorSearchPerformanceContext.info("意图类型识别完成：type={}，消息数={}",
                intentTypeEnum.getName(), chatCompletionRequest.getMessages() == null
                        ? 0 : chatCompletionRequest.getMessages().size());
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

    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest) {
        return detectIntent(chatCompletionRequest, null);
    }

    private static void setIntentByVector(ChatCompletionRequest chatCompletionRequest, Integer lIndex, String lastQ, IntentResult intentResult, Map<String, Object> where) {
        long comparisonStartedNanos = VectorSearchPerformanceContext.startTimer();
        VectorStoreService vectorStoreService = new VectorStoreService();
        String lQ = chatCompletionRequest.getMessages().get(lIndex).getContent();
        String complexQ = lQ + lastQ;
        lastQ = StrFilterUtil.filterPunctuations(lastQ);
        complexQ = StrFilterUtil.filterPunctuations(complexQ);
        String finalLastQ = lastQ;
        VectorSearchPerformanceContext.TimedFuture<List<IndexSearchData>> lastFuture =
                VectorSearchPerformanceContext.submit(executor, "vector-intent", "intent-vector", "last-question",
                        () -> vectorStoreService.search(finalLastQ, where, chatCompletionRequest.getCategory()));
        String finalComplexQ = complexQ;
        VectorSearchPerformanceContext.TimedFuture<List<IndexSearchData>> complexFuture =
                VectorSearchPerformanceContext.submit(executor, "vector-intent", "intent-vector", "complex-question",
                        () -> vectorStoreService.search(finalComplexQ, where, chatCompletionRequest.getCategory()));
        try {
            List<IndexSearchData> l = lastFuture.get();
            List<IndexSearchData> c = complexFuture.get();
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
            VectorSearchPerformanceContext.info(
                    "意图向量比较完成：last结果数={}，complex结果数={}，选择继续上下文={}",
                    l.size(), c.size(), vectorContinue);
        } catch (Exception e) {
            log.error("detectIntent error", e);
            VectorSearchPerformanceContext.error("意图向量比较异常", e);
        } finally {
            VectorSearchPerformanceContext.recordStage("intent.vectorComparison", "意图并行向量比较",
                    VectorSearchPerformanceContext.elapsedMillis(comparisonStartedNanos),
                    VectorSearchPerformanceContext.TASK_EXECUTION_WARN_MS);
        }
    }
}
