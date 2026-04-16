package ai.llm.adapter.impl;

public class ZhipuAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        }
        return apiAddress;
    }
}
