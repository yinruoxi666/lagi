package ai.llm.utils.convert;

import ai.common.exception.RRException;
import ai.llm.pojo.ArvryuyiCompletionResult;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import com.google.gson.Gson;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;

public class ArvryuyiConvert {
    private static final String HEAD_FLAG = "^^^###";
    private static final String TAIL_FLAG = "^^^&&&";
    private static final Gson gson = new Gson();
    public static int convert(Object object) {
        if(object instanceof  Integer) {
            return convertByInt((int) object);
        }
        if(object instanceof Response) {
            return convertByResponse((Response) object);
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

//    InvalidAuthenticationInfo	错误的验证信息 (3008)	未以正确的格式提供身份验证信息。 验证 Authorization 标头的值。


    public static int convertByInt(int errorCode) {
        if(errorCode == 400 || 405 == errorCode) {
            return LLMErrorConstants.INVALID_REQUEST_ERROR;
        }
        if(errorCode == 3008) {
            return LLMErrorConstants.INVALID_AUTHENTICATION_ERROR;
        }
        if(errorCode == 403) {
            return LLMErrorConstants.PERMISSION_DENIED_ERROR;
        }
        if(errorCode == 404) {
            return LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR;
        }
        if(errorCode == 503) {
            return LLMErrorConstants.RATE_LIMIT_REACHED_ERROR;
        }
        if(errorCode == 500) {
            return LLMErrorConstants.SERVER_ERROR;
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

    public static ChatCompletionResult convert2ChatCompletionResult(String body) {
        if(body == null) {
            return null;
        }
        return gson.fromJson(body, ChatCompletionResult.class);
    }

    public static ChatCompletionResult convertStreamLine2ChatCompletionResult(String body) {
        if (body.equals("[DONE]")) {
            return null;
        }
        String jsonStr = body.substring(HEAD_FLAG.length(), body.length() - TAIL_FLAG.length());
        ArvryuyiCompletionResult arvryuyiResult = gson.fromJson(jsonStr, ArvryuyiCompletionResult.class);
        ChatCompletionResult result = new ChatCompletionResult();
        List<ChatCompletionChoice> choices = new ArrayList<>();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(arvryuyiResult.getData().getCurdata().getAnswer());
        choice.setMessage(message);
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }

    public static int convertByResponse(Response response) {
        return convert(response.code());
    }

    public static RRException convert2RResponse(Response response) {
        return new RRException(convertByResponse(response), response.message());
    }
}
