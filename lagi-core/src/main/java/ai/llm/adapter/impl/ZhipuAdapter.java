package ai.llm.adapter.impl;

import ai.annotation.LLM;

@LLM(modelNames = {"glm-3-turbo","glm-4","glm-4v","glm-4-alltools","glm-4.6"})
public class ZhipuAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        }
        return apiAddress;
    }
}
