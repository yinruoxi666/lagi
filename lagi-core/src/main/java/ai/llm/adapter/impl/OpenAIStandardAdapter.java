package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


@LLM(modelNames = {"*"})
public class OpenAIStandardAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIStandardAdapter.class);
    private static final int HTTP_TIMEOUT = 30 * 1000;
    private static final Gson GSON = new Gson();

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        String json = buildRequestJson(chatCompletionRequest);
        LlmApiResponse completions = OpenAiApiUtil.completions(apiKey, getApiAddress(), HTTP_TIMEOUT, json,
                GptConvert::convert2ChatCompletionResult, GptConvert::convertByResponse, getHeaders(), null);
        if(completions.getCode() != 200) {
            logger.error("openai api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }



    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        String json = buildRequestJson(chatCompletionRequest);
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, getApiAddress(), HTTP_TIMEOUT, json,
                GptConvert::convertSteamLine2ChatCompletionResult, GptConvert::convertByResponse, getStreamHeaders());
        if(completions.getCode() != 200) {
            logger.error("openai  stream api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    String buildRequestJson(ChatCompletionRequest request) {
        JsonObject requestJson = GSON.toJsonTree(request).getAsJsonObject();
        String model = request.getModel();
        if (isQwenThinkingModel(model)) {
            JsonElement enableThinkingElement = requestJson.get("enable_thinking");
            if (enableThinkingElement == null) {
                requestJson.addProperty("enable_thinking", false);
            }
        }
        return GSON.toJson(requestJson);
    }

    private boolean isQwenThinkingModel(String model) {
        if (model == null) {
            return false;
        }
        String lowerModelName = model.toLowerCase();
        return lowerModelName.contains("qwen3.5") || lowerModelName.contains("qwen3.6");
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private Map<String, String> getStreamHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }
}
