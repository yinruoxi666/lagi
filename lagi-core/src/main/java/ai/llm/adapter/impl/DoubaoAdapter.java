package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.openai.pojo.ChatCompletionRequest;

@LLM(modelNames = {"doubao-pro-4k","doubao-pro-32k","doubao-seed"})
public class DoubaoAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
        }
        return apiAddress;
    }

    @Override
    protected void setDefaultField(ChatCompletionRequest request) {
        super.setDefaultField(request);
        if (Boolean.TRUE.equals(function) || function == null) {
            request.setTool_choice("auto");
        }
    }

}
