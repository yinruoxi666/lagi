package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.utils.MappingIterable;
import ai.common.utils.ObservableList;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.ServerSentEventUtil;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.HttpUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@LLM(modelNames = { "glm-3-turbo","glm-4", "glm-4v"})
public class TopiaAdapter extends ModelService implements ILlmAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TopiaAdapter.class);

    private final Gson gson = new Gson();
    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String SIGN_URL = "https://pro.ai-topia.com/apis/login";

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

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + "eyJhbGciOiJIUzUxMiJ9.eyJhdXRoX3R5cGUiOiIyIiwidXNlcl9pZCI6NjU3MzIsInVzZXJfa2V5IjoiNGFlMzM1MjM0OGU5NDI1NTk0OTVhM2RhNzY2MTBmYzgiLCJ1c2VybmFtZSI6IjEzNTIwMzYxNDgzIn0.p6S-TrFQh6KPudjVcvT3QUO-OxZ_RPErSqxKOjxyHTyY4ZnJMtqUWuJsAWfyKsxJmne8Dcr8m9gALMT-APUu-w");
        String jsonResult = null;
        chatCompletionRequest.setCategory(null);
        try {
            jsonResult = HttpUtil.httpPost(COMPLETIONS_URL, headers, chatCompletionRequest, HTTP_TIMEOUT);
        } catch (IOException e) {
            logger.error("", e);
        }
        ChatCompletionResult response = new ChatCompletionResult();
        return response;
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + "eyJhbGciOiJIUzUxMiJ9.eyJhdXRoX3R5cGUiOiIyIiwidXNlcl9pZCI6NjU3MzIsInVzZXJfa2V5IjoiNGFlMzM1MjM0OGU5NDI1NTk0OTVhM2RhNzY2MTBmYzgiLCJ1c2VybmFtZSI6IjEzNTIwMzYxNDgzIn0.p6S-TrFQh6KPudjVcvT3QUO-OxZ_RPErSqxKOjxyHTyY4ZnJMtqUWuJsAWfyKsxJmne8Dcr8m9gALMT-APUu-w");
        String json = gson.toJson(chatCompletionRequest);
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
        ObservableList<ChatCompletionResult> result = null;
//                ServerSentEventUtil.streamCompletions(json, COMPLETIONS_URL, apiKey, convertFunc, this);
        Iterable<ChatCompletionResult> iterable = result.getObservable().blockingIterable();
        return Observable.fromIterable(iterable);
    }

    private com.zhipu.oapi.service.v4.model.ChatCompletionRequest convertRequest(ChatCompletionRequest request) {
        List<com.zhipu.oapi.service.v4.model.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            com.zhipu.oapi.service.v4.model.ChatMessage msg =
                    new com.zhipu.oapi.service.v4.model.ChatMessage(chatMessage.getRole(), chatMessage.getContent());
            messages.add(msg);
        }

        boolean stream = Optional.ofNullable(request.getStream()).orElse(false);
        String model = Optional.ofNullable(request.getModel()).orElse(getModel());

        String invokeMethod = Constants.invokeMethod;
        if (stream) {
            invokeMethod = Constants.invokeMethodSse;
        }

        return com.zhipu.oapi.service.v4.model.ChatCompletionRequest.builder()
                .model(model)
                .maxTokens(request.getMax_tokens())
                .temperature((float) request.getTemperature())
                .stream(stream)
                .invokeMethod(invokeMethod)
                .messages(messages)
                .build();
    }

    private ChatCompletionResult convertResponse(ModelData modelData) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(modelData.getId());
        result.setCreated(modelData.getCreated());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        for (int i = 0; i < modelData.getChoices().size(); i++) {
            ChatCompletionChoice choice = new ChatCompletionChoice();
            ChatMessage chatMessage = new ChatMessage();
            com.zhipu.oapi.service.v4.model.Delta delta = modelData.getChoices().get(i).getDelta();
            if (delta != null) {
                chatMessage.setContent(delta.getContent());
                chatMessage.setRole(delta.getRole());
            } else {
                com.zhipu.oapi.service.v4.model.ChatMessage message = modelData.getChoices().get(i).getMessage();
                chatMessage.setContent(message.getContent().toString());
                chatMessage.setRole(message.getRole());
            }
            choice.setIndex(i);
            choice.setMessage(chatMessage);
            choices.add(choice);
        }
        result.setChoices(choices);
        return result;
    }

    static String sign(String ak,String sk) {
        Map<String,String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        Map<String, String> body = new HashMap<>();
        body.put("appId", "topia_ba80fd12f4cc47");
        body.put("appSecret", "6c28b3fe6298424282c3b3ba4bb3fb65");
        Gson gson = new Gson();
        String jsonBody = gson.toJson(body);
        String jsonResult = HttpUtil.httpPost(COMPLETIONS_URL, headers, jsonBody, HTTP_TIMEOUT);
        return gson.fromJson(jsonResult, Map.class).get("access_token").toString();
    }
}
