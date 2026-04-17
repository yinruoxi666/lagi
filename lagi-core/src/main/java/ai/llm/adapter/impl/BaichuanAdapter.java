package ai.llm.adapter.impl;


public class BaichuanAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.baichuan-ai.com/v1/chat/completions";
        }
        return apiAddress;
    }
}
