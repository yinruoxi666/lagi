package ai.llm.hook;

import ai.openai.pojo.ChatCompletionRequest;

public interface BeforeModel {
    /**
     * Tasks to be performed before model invocation begins
     * @param request openai request
     */
    ChatCompletionRequest beforeModel(ChatCompletionRequest request);
}
