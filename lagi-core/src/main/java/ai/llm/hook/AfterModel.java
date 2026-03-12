package ai.llm.hook;

import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;

public interface AfterModel {

    ChatCompletionResult apply(ChatCompletionResult result);
    Observable<ChatCompletionResult> stream(Observable<ChatCompletionResult>  result);
}
