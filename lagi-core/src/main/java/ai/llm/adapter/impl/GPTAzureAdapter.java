package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.common.pojo.Backend;
import ai.common.utils.ObservableList;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.ServerSentEventUtil;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.qa.HttpUtil;
import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class GPTAzureAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GPTAzureAdapter.class);
    private final Gson gson = new Gson();
    private static final String ENTERPOINT = "https://api.openai.com/v1/chat/completions";
    private static final int HTTP_TIMEOUT = 15 * 1000;

    private final Backend backendConfig;

    public GPTAzureAdapter(Backend backendConfig) {
        this.backendConfig = backendConfig;
    }

    @Override
    public String getApiAddress() {
        return backendConfig.getEndpoint() + "openai/deployments/" + backendConfig.getDeployment() + "/chat/completions?api-version=" + backendConfig.getApiVersion();
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultModel(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + backendConfig.getApiKey());
        String jsonResult = null;
        chatCompletionRequest.setCategory(null);
        try {
            jsonResult = HttpUtil.httpPost(ENTERPOINT, headers, chatCompletionRequest, HTTP_TIMEOUT);
        } catch (IOException e) {
            logger.error("", e);
        }
        if (jsonResult == null) {
            return null;
        }
        ChatCompletionResult response = gson.fromJson(jsonResult, ChatCompletionResult.class);
        if (response == null || response.getChoices().isEmpty()) {
            return null;
        }
        return response;
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultModel(chatCompletionRequest);
        chatCompletionRequest.setCategory(null);
        String apiUrl = getApiAddress();
        String json = gson.toJson(chatCompletionRequest);
        String apiKey = backendConfig.getApiKey();
        Function<String, ChatCompletionResult> convertFunc = e -> {
            if (e.equals("[DONE]")) {
                return null;
            }
            ChatCompletionResult result = gson.fromJson(e, ChatCompletionResult.class);
            result.getChoices().forEach(choice -> {
                choice.setMessage(choice.getDelta());
                choice.setDelta(null);
            });
            return result;
        };
        Map<String,String> addHeader = new HashMap() {{
            put("api-key", apiKey);
        }};
        ObservableList<ChatCompletionResult> result =
                ServerSentEventUtil.streamCompletions(json, apiUrl, apiKey,addHeader, convertFunc);
        Iterable<ChatCompletionResult> iterable = result.getObservable().blockingIterable();
        return Observable.fromIterable(iterable);
    }

    private void setDefaultModel(ChatCompletionRequest request) {
        if (request.getModel() == null) {
            request.setModel(backendConfig.getModel());
        }
    }
}
