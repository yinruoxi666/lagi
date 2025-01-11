package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.utils.ObservableList;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.ArvryuyiChatCompletionRequest;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.ArvryuyiConvert;
import ai.llm.utils.convert.GptAzureConvert;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.HttpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@LLM(modelNames = {"arvryuyi"})
public class ArvryuyiAdapter implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ArvryuyiAdapter.class);
    private final Gson gson = new Gson();
    private static final int HTTP_TIMEOUT = 30 * 1000;
    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
//        setDefaultModel(request);
        ArvryuyiChatCompletionRequest arvryuyiChatCompletionRequest = (ArvryuyiChatCompletionRequest) request;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String jsonResult = null;
        request.setCategory(null);
        JSONObject requestJson = new JSONObject();
        requestJson.put("appkey", "46733500-9c63-4a30-b15f-01d965184adf");
        requestJson.put("secret", "9cd518e4-4ca4-4fe3-8f63-51626ae39e16");
        requestJson.put("mac", "");
        String sn = StrUtil.isNotBlank(arvryuyiChatCompletionRequest.getSn()) ? arvryuyiChatCompletionRequest.getSn() : "b3775d32-2250-4fe2-9a69-9c8254448655";
        requestJson.put("sn", sn);
        requestJson.put("robotid", "cda998f6-6f9b-4466-b052-3e726bb5cd13");
        requestJson.put("text", request.getMessages().get(request.getMessages().size() - 1).getContent());
        requestJson.put("appmsg", "");
        jsonResult = HttpUtil.httpPost("https://arvryuyi.caacsri.com/tech/yuyi/data", headers, requestJson.toJSONString(), HTTP_TIMEOUT);
        if (jsonResult == null) {
            return null;
        }

        JSONObject resultJson = JSONObject.parseObject(jsonResult);
        JSONObject responseJson = resultJson.getJSONObject("data").getJSONObject("curdata");
        if (responseJson == null) {
            return null;
        }
        ChatCompletionResult response = new ChatCompletionResult();
        Integer code = resultJson.getInteger("code");
        if (code != 0) {
            return null;
        }
        response.setChoices(new ArrayList<>());
        ChatCompletionChoice choice= new ChatCompletionChoice();
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(responseJson.getString("answer"));
        choice.setMessage(message);
        choice.setFinish_reason("stop");
        response.getChoices().add(choice);

        if (response == null || response.getChoices().isEmpty()) {
            return null;
        }
        return response;
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
        ArvryuyiChatCompletionRequest arvryuyiChatCompletionRequest = (ArvryuyiChatCompletionRequest) request;
//        setDefaultModel(request);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept","text/event-stream");
        headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String jsonResult = null;
        request.setCategory(null);
        JSONObject requestJson = new JSONObject();
        requestJson.put("appkey", "46733500-9c63-4a30-b15f-01d965184adf");
        requestJson.put("secret", "9cd518e4-4ca4-4fe3-8f63-51626ae39e16");
        requestJson.put("mac", "");
        requestJson.put("appmsg", "");
        String sn = StrUtil.isNotBlank(arvryuyiChatCompletionRequest.getSn()) ? arvryuyiChatCompletionRequest.getSn() : "b3775d32-2250-4fe2-9a69-9c8254448655";
        requestJson.put("sn", sn);
        requestJson.put("robotid", "cda998f6-6f9b-4466-b052-3e726bb5cd13");
        requestJson.put("text", request.getMessages().get(request.getMessages().size() - 1).getContent());
        System.out.println(requestJson.toJSONString());
        LlmApiResponse llmApiResponse = OpenAiApiUtil.streamCompletions("", getApiAddress(), HTTP_TIMEOUT, requestJson.toJSONString(),
                ArvryuyiConvert::convertStreamLine2ChatCompletionResult, ArvryuyiConvert::convertByResponse, headers);

//        JSONObject resultJson = JSONObject.parseObject(strResult);
//        JSONObject responseJson = resultJson.getJSONObject("data").getJSONObject("curdata");
//        if (responseJson == null) {
//            return null;
//        }
//        ChatCompletionResult response = new ChatCompletionResult();
//        Integer code = resultJson.getInteger("code");
//        if (code != 0) {
//            return null;
//        }
//        response.setChoices(new ArrayList<>());
//        ChatCompletionChoice choice= new ChatCompletionChoice();
//        ChatMessage message = new ChatMessage();
//        message.setRole("assistant");
//        message.setContent(responseJson.getString("answer"));
//        choice.setMessage(message);
//        choice.setFinish_reason("stop");
//        response.getChoices().add(choice);
//        ObservableList<ChatCompletionResult> result = new ObservableList<>();
//        result.add(response);
//        result.onComplete();
//        Iterable<ChatCompletionResult> iterable = result.getObservable().blockingIterable();
        return llmApiResponse.getStreamData();
    }

    private String getApiAddress() {
        return "https://arvryuyi.caacsri.com/tech/yuyi/data/stream";
    }

//    private void setDefaultModel(ChatCompletionRequest request) {
//        if (request.getModel() == null) {
//            request.setModel(getModel());
//        }
//    }
}
