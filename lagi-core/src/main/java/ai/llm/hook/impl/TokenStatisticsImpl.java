package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.llm.dao.TokenStatisticsDao;
import ai.llm.hook.AfterModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import io.reactivex.Observable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(3)
@Component
public class TokenStatisticsImpl implements AfterModel {

    private static final ExecutorService ASYNC_SAVE =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "llm-token-statistics");
                t.setDaemon(true);
                return t;
            });

    private final TokenStatisticsDao tokenStatisticsDao = new TokenStatisticsDao();

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        if (result != null && result.getUsage() != null) {
            recordUsage(result.getUsage());
        }
        return result;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        AtomicBoolean recorded = new AtomicBoolean(false);
        return context.getStreamResult().doOnNext(chunk -> {
            if (recorded.get()) {
                return;
            }
            if (chunk != null && chunk.getUsage() != null) {
                Usage u = chunk.getUsage();
                if (u.getTotal_tokens() > 0) {
                    recorded.set(true);
                    recordUsage(u);
                }
            }
        });
    }

    private void recordUsage(Usage usage) {
        long total = usage.getTotal_tokens();
        if (total <= 0) {
            return;
        }
        long saved = TokenStatisticsDao.computeSavedTokens(total);
        usage.setSaved_tokens(saved);
        long prompt = usage.getPrompt_tokens();
        long completion = usage.getCompletion_tokens();
        ASYNC_SAVE.execute(() -> tokenStatisticsDao.insert(prompt, completion, total, saved));
    }
}
