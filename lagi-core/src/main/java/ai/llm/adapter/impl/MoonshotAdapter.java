package ai.llm.adapter.impl;


public class MoonshotAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.moonshot.cn/v1/chat/completions";
        }
        return apiAddress;
    }
}
