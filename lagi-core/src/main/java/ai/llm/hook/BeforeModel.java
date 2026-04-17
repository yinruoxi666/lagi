package ai.llm.hook;

import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;

public interface BeforeModel {
    /**
     * Tasks to be performed before model invocation begins
     * @param request openai request
     */
    ChatCompletionRequest beforeModel(ModelContext request);
}
