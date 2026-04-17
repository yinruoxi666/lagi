package ai.llm.adapter.impl;


import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;

public class LandingAdapter extends OpenAIStandardAdapter {
    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        normalizeModelNameIfSlashSeparated(chatCompletionRequest);
        setApiKey(chatCompletionRequest.getApiKey());
        return super.completions(chatCompletionRequest);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        normalizeModelNameIfSlashSeparated(chatCompletionRequest);
        setApiKey(chatCompletionRequest.getApiKey());
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

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://lagi.saasai.top/v1/chat/completions";
        }
        return apiAddress;
    }

    @Override
    public boolean verify() {
        return true;
    }

    @Override
    public void setApiKey(String apiKey) {
        if (getApiKey() == null) {
            this.apiKey = apiKey;
        }
    }
}
