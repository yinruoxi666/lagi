package ai.llm.adapter.impl;

public class DoubaoAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
        }
        return apiAddress;
    }
}