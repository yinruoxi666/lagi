package ai.llm.pojo;

import ai.llm.adapter.ILlmAdapter;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class ModelContext {
    private ChatCompletionRequest request;
    private ILlmAdapter adapter;
    private ChatCompletionResult result;
    private Observable<ChatCompletionResult> streamResult;
}
