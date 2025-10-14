package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.utils.ObservableList;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.ServerSentEventUtil;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.HttpUtil;
import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.reactivex.Observable;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@LLM(modelNames = {"psychology-base-v1"})
public class PsychologyBaseAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PsychologyBaseAdapter.class);
    private final Gson gson = new Gson();
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
        chatCompletionRequest.setCategory(null);
        String userId = chatCompletionRequest.getSessionId();
        chatCompletionRequest.setSessionId(null);
        JsonElement je = gson.toJsonTree(chatCompletionRequest);
        String device_id = je.getAsJsonObject().get("agent_config").getAsJsonObject().get("psychology-base-v1").getAsJsonObject().get("device_id").getAsString();
        String track_id = je.getAsJsonObject().get("agent_config").getAsJsonObject().get("psychology-base-v1").getAsJsonObject().get("track_id").getAsString();
        je.getAsJsonObject().addProperty("device_id", device_id);
        je.getAsJsonObject().addProperty("track_id", track_id);
        je.getAsJsonObject().remove("agent_config");
        String json = je.toString();
        System.out.println(json);
        json = addParamToJson(json, "userId", userId, getAppId());
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

    // 在json中添加userId字段后返回sting格式
    private String addParamToJson(String json,String paramName, String paramValue, String defaultValue) {
        if (getAppId() == null || getAppId().isEmpty()) {
            return json;
        }

        int insertIndex = json.lastIndexOf('}');
        if (insertIndex == -1) {
            return json;
        }
        if (paramName == null || paramName.isEmpty()) {
            return json;
        }
        if (defaultValue != null  && !defaultValue.isEmpty()) {
            paramValue = paramValue == null ? defaultValue : paramValue;
        }
        String userIdField = ",\"" + paramName + "\":\"" + paramValue + "\"";
        return json.substring(0, insertIndex) + userIdField + json.substring(insertIndex);
    }


    private void limitChatMessages(ChatCompletionRequest request) {
        if (request.getMessages() != null ) {
            request.setMessages(Collections.singletonList(request.getMessages().get(request.getMessages().size() - 1)));
        }
    }
}
