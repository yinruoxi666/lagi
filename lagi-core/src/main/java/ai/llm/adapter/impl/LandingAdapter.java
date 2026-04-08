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
        ensureApiKeyValid();
        return super.completions(chatCompletionRequest);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        ensureApiKeyValid();
        return super.streamCompletions(chatCompletionRequest);
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
