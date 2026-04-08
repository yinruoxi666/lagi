package ai.migrate.service;

import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import ai.utils.AiGlobal;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TokenUsageService {
    private static final Gson gson = new Gson();
    private static final TokenUsageService INSTANCE = new TokenUsageService();
    private static final String TOKEN_USAGE_REPORT_URL = AiGlobal.SAAS_URL + "/saas/api/token/usage/report/mock";
    private static final long RETRY_INTERVAL_MILLIS = 3000L;
    private static final int CONSUMER_THREADS = 3;
    private final TokenUsageQueue tokenUsageQueue = TokenUsageQueue.getInstance();

    private TokenUsageService() {
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            ExecutorService reportExecutor = Executors.newFixedThreadPool(CONSUMER_THREADS, r -> {
                Thread thread = new Thread(r, "token-usage-report");
                thread.setDaemon(true);
                return thread;
            });
            reportExecutor.submit(this::reportUsageToSaasAsync);
        }
    }

    public static TokenUsageService getInstance() {
        return INSTANCE;
    }

    public void recordUsage(String apiKey, Usage usage) {
        boolean offered = tokenUsageQueue.enqueue(apiKey, usage);
        if (!offered) {
            log.warn("Token usage enqueue skipped");
        }
    }

    public void recordUsage(String apiKey, ChatCompletionResult result) {
        if (result == null) {
            return;
        }
        recordUsage(apiKey, result.getUsage());
    }

    public void reportUsageToSaasAsync() {
        while (true) {
            TokenUsageQueue.TokenUsageRecord record = takeRecord();
            if (record == null) {
                continue;
            }
            String body = buildPayload(record.getApiKey(), record.getUsage());
            while (true) {
                try {
                    log.info("Sending token usage report: {}", body);
                    OkHttpUtil.post(TOKEN_USAGE_REPORT_URL, body);
                    break;
                } catch (Exception e) {
                    log.warn("Token usage report failed, retrying until success", e);
                    sleepSilently(RETRY_INTERVAL_MILLIS);
                }
            }
        }
    }

    private String buildPayload(String apiKey, Usage usage) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("apiKey", apiKey);
        payload.put("promptTokens", usage == null ? 0L : usage.getPrompt_tokens());
        payload.put("completionTokens", usage == null ? 0L : usage.getCompletion_tokens());
        payload.put("totalTokens", usage == null ? 0L : usage.getTotal_tokens());
        return gson.toJson(payload);
    }

    private void sleepSilently(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
            // Ignore interruption to keep background consumer alive.
        }
    }

    private TokenUsageQueue.TokenUsageRecord takeRecord() {
        try {
            return tokenUsageQueue.take();
        } catch (InterruptedException ignored) {
            return null;
        }
    }
}
