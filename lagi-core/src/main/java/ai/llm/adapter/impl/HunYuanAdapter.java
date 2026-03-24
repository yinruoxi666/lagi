package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.openai.pojo.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@LLM(modelNames = {"hunyuan-2.0-thinking-20251109,hunyuan-2.0-instruct-20251111,hunyuan-t1-latest,hunyuan-a13b,hunyuan-turbos-latest,hunyuan-lite"})
public class HunYuanAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HunYuanAdapter.class);

    @Override
    protected void setDefaultField(ChatCompletionRequest request) {
        super.setDefaultField(request);
        if(Boolean.TRUE.equals(request.getStream())) {
            Map<String, Object> streamOptions = request.getStream_options();
            if(streamOptions != null) {
                streamOptions.put("include_usage", true);
            } else {
                streamOptions = new HashMap<>();
                streamOptions.put("include_usage", true);
            }
            request.setStream_options(streamOptions);
        }
    }

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.hunyuan.cloud.tencent.com/v1/chat/completions";
        }
        return apiAddress;
    }
}
