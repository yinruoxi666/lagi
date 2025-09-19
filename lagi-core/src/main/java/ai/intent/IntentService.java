package ai.intent;

import ai.intent.pojo.IntentResult;
import ai.openai.pojo.ChatCompletionRequest;

import java.util.Map;

public interface IntentService {
    IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest, Map<String, Object> where);

    IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest);
}
