package ai.migrate.service;

import ai.openai.pojo.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TokenUsageQueue {
    private static final Logger logger = LoggerFactory.getLogger(TokenUsageQueue.class);
    private static final TokenUsageQueue INSTANCE = new TokenUsageQueue();
    private final BlockingQueue<TokenUsageRecord> queue = new LinkedBlockingQueue<TokenUsageRecord>();

    private TokenUsageQueue() {
    }

    public static TokenUsageQueue getInstance() {
        return INSTANCE;
    }

    public boolean enqueue(String apiKey, Usage usage) {
        if (usage == null) {
            return false;
        }
        // offer is non-blocking; it returns immediately.
        return queue.offer(new TokenUsageRecord(apiKey, usage));
    }

    public TokenUsageRecord poll() {
        return queue.poll();
    }

    public TokenUsageRecord take() throws InterruptedException {
        return queue.take();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "-";
        }
        String value = apiKey.trim();
        if (value.length() <= 8) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public static class TokenUsageRecord {
        private final String apiKey;
        private final Usage usage;

        public TokenUsageRecord(String apiKey, Usage usage) {
            this.apiKey = apiKey;
            this.usage = usage;
        }

        public String getApiKey() {
            return apiKey;
        }

        public Usage getUsage() {
            return usage;
        }
    }
}
