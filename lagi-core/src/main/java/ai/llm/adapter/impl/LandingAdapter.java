package ai.llm.adapter.impl;


import ai.common.exception.RRException;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.ApikeyUtil;
import io.reactivex.Observable;

public class LandingAdapter extends OpenAIStandardAdapter {
    private static final int UNAUTHORIZED_CODE = 401;
    private static final String INVALID_API_KEY_MSG = "Landing apiKey is invalid";

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        normalizeModelNameIfSlashSeparated(chatCompletionRequest);
        ensureApiKeyValid();
        return super.completions(chatCompletionRequest);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        normalizeModelNameIfSlashSeparated(chatCompletionRequest);
        ensureApiKeyValid();
        return super.streamCompletions(chatCompletionRequest);
    }

    /**
     * If model is like {@code Alibaba/qwen3-max}, keep only the part after the slash ({@code qwen3-max}).
     */
    private void normalizeModelNameIfSlashSeparated(ChatCompletionRequest chatCompletionRequest) {
        if (chatCompletionRequest == null || chatCompletionRequest.getModel() == null) {
            return;
        }
        String model = chatCompletionRequest.getModel().trim();
        int slash = model.indexOf('/');
        if (slash >= 0 && slash < model.length() - 1) {
            String after = model.substring(slash + 1).trim();
            if (!after.isEmpty()) {
                chatCompletionRequest.setModel(after);
            }
        }
    }

    private void ensureApiKeyValid() {
        if (!ApikeyUtil.validateModelApiKey(getApiKey())) {
            throw new RRException(UNAUTHORIZED_CODE, INVALID_API_KEY_MSG);
        }
    }

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://lagi.saasai.top/v1/chat/completions";
        }
        return apiAddress;
    }
}
