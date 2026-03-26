package ai.llm.utils.convert;

import ai.common.exception.RRException;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Status;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;


public class QwenConvert {

    private static Gson gson = new Gson();
    private static final String DATA_INSPECTION_FAILED = "datainspectionfailed";
    private static final String DATA_INSPECTION_FAILED_SNAKE = "data_inspection_failed";
    private static final String SAFETY_REJECTED = "user request is rejected by safety system";
    private static final String SAFETY_BLOCKED = "safety system";
    private static final String SENSITIVE_BLOCKED = "contains suspected sensitive";

    public static Integer convertByHttpResponse(Response response) {
        int code = response.code();
        if(code == 400) {
            return LLMErrorConstants.INVALID_REQUEST_ERROR;
        }
        if(code == 401) {
            return LLMErrorConstants.INVALID_AUTHENTICATION_ERROR;
        }
        if(code == 403) {
            return LLMErrorConstants.PERMISSION_DENIED_ERROR;
        }
        if(code == 404) {
            return LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR;
        }
        if(code == 408) {
            return LLMErrorConstants.TIME_OUT;
        }
        if(code == 429) {
            return LLMErrorConstants.RATE_LIMIT_REACHED_ERROR;
        }
        if(code == 500) {
            return LLMErrorConstants.SERVER_ERROR;
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

   public static RRException convert2RRexception(Exception e) {
        if(e instanceof NoApiKeyException) {
           return new RRException(LLMErrorConstants.INVALID_AUTHENTICATION_ERROR, e.getMessage());
        }
        if(e instanceof InputRequiredException) {
            return new RRException(LLMErrorConstants.INVALID_REQUEST_ERROR, e.getMessage());
        }
        if(e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            Status status = apiException.getStatus();
            String msg = JSONUtil.toJsonStr(status);
            if (isSafetyBlockedMessage(msg)) {
                return new RRException(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, msg);
            }
            Integer code = status.getStatusCode();
//            https://help.aliyun.com/zh/model-studio/developer-reference/error-code?spm=a2c4g.11186623.0.i1
            if(code == 400) {
                return new RRException(LLMErrorConstants.INVALID_REQUEST_ERROR, msg);
            }
            if(code == 401) {
                return new RRException(LLMErrorConstants.INVALID_AUTHENTICATION_ERROR, msg);
            }
            if(code == 403) {
                return new RRException(LLMErrorConstants.PERMISSION_DENIED_ERROR, msg);
            }
            if(code == 404) {
                return new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, msg);
            }
            if(code == 408) {
                return new RRException(LLMErrorConstants.TIME_OUT, msg);
            }
            if(code == 429) {
                return new RRException(LLMErrorConstants.RATE_LIMIT_REACHED_ERROR, msg);
            }
            if(code == 500) {
                return new RRException(LLMErrorConstants.SERVER_ERROR, msg);
            }
        }
        return new RRException(LLMErrorConstants.OTHER_ERROR, "{\"error\": \"unknown error\"}");
   }

    public static RRException convertResponseException(Integer code, String msg) {
        if (isSafetyBlockedMessage(msg)) {
            return new RRException(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, msg);
        }
        return new RRException(code, msg);
    }

    public static boolean isSafetyBlockedMessage(String msg) {
        if (msg == null) {
            return false;
        }
        String normalized = msg.toLowerCase(Locale.ROOT);
        return normalized.contains(DATA_INSPECTION_FAILED)
                || normalized.contains(DATA_INSPECTION_FAILED_SNAKE)
                || normalized.contains(SAFETY_REJECTED)
                || (normalized.contains(SAFETY_BLOCKED) && normalized.contains("reject"))
                || normalized.contains(SENSITIVE_BLOCKED);
    }

   public static ChatCompletionResult convertStreamLine2ChatCompletionResult(String body) {
       if (body.equals("[DONE]")) {
           return null;
       }
       GenerationResult result = gson.fromJson(body, GenerationResult.class);
       return convertResponse(result);
   }

    public   static  ChatCompletionResult convertResponse(GenerationResult response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(response.getRequestId());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(response.getOutput().getChoices().get(0).getMessage().getContent());
        chatMessage.setRole("assistant");
        choice.setMessage(chatMessage);
        choice.setFinish_reason(response.getOutput().getFinishReason());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }

}
