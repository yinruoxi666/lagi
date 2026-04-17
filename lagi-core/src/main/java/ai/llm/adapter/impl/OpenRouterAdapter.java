package ai.llm.adapter.impl;

import ai.annotation.LLM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LLM(modelNames = {"*"})
public class OpenRouterAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterAdapter.class);

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://openrouter.ai/api/v1/chat/completions";
        }
        return apiAddress;
    }
}
