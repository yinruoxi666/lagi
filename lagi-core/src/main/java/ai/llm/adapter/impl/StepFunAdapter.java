package ai.llm.adapter.impl;

import ai.annotation.LLM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LLM(modelNames = {"step-3.5-flash,step-2-mini,step-2,step-1"})
public class StepFunAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(StepFunAdapter.class);

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.stepfun.com/v1/chat/completions";
        }
        return apiAddress;
    }
}
