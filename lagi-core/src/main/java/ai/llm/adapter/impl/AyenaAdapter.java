package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.utils.ObservableList;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.ServerSentEventUtil;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.HttpUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import io.reactivex.Observable;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@LLM(modelNames = {"ayenaspring-advanced-001"})
public class AyenaAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AyenaAdapter.class);
    private final Gson gson = new Gson();
    private static final String COMPLETIONS_URL = "https://www.ayenaspring.com:8081/v2/chat/completions";
    private static final int HTTP_TIMEOUT = 15 * 1000;



    @Override
    public boolean verify() {
        if(getApiKey() == null || getApiKey().startsWith("you")) {
            return false;
        }
        return true;
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultModel(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + getApiKey());
        String jsonResult = null;
        chatCompletionRequest.setCategory(null);
        chatCompletionRequest.setStream(true);
        try {
            jsonResult = HttpUtil.httpPost(getApiAddress(), headers, chatCompletionRequest, HTTP_TIMEOUT);
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
        String json = gson.toJson(chatCompletionRequest);
        String apiKey = getApiKey();
        Function<String, ChatCompletionResult> convertFunc = e -> {
            if (e.equals("[DONE]")) {
                return null;
            }
            ChatCompletionResult result = gson.fromJson(e, ChatCompletionResult.class);
            result.getChoices().forEach(choice -> {
                choice.setMessage(choice.getDelta());
                choice.setDelta(null);
            if (choice.getMessage() != null && choice.getMessage().getRole() != null && choice.getMessage().getRole().equals("docs")) {
                choice.setMessage(new ChatMessage());
            }
            });
//            if (result.getChoices().get(0).getMessage() != null && result.getChoices().get(0).getMessage().getRole() != null && result.getChoices().get(0).getMessage().getRole().equals("docs")) {
//                return null;
//            }
            return result;
        };
        Function<Response, RRException> convertErrorFunc = response -> new RRException();
        ObservableList<ChatCompletionResult> result =
                ServerSentEventUtil.streamCompletions(json, getApiAddress(), apiKey, convertFunc, this, convertErrorFunc);
        Iterable<ChatCompletionResult> iterable = result.getObservable().blockingIterable();
        return Observable.fromIterable(iterable);
    }

    private void setDefaultModel(ChatCompletionRequest request) {
        if (request.getModel() == null) {
            request.setModel(getModel());
        }
    }
}
