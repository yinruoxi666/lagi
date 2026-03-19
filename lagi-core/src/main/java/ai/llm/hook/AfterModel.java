package ai.llm.hook;

import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;

public interface AfterModel {

    ChatCompletionResult apply(ModelContext context);
    Observable<ChatCompletionResult> stream(ModelContext  context);
}
