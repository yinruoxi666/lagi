package ai.llm.adapter.impl;

import ai.annotation.LLM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LLM(modelNames = {"mimo-v2-pro,mimo-v2-omni"})
public class XiaoMiAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(XiaoMiAdapter.class);

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.xiaomimimo.com/v1/chat/completions";
        }
        return apiAddress;
    }
}
