package ai.llm.pojo;

import ai.openai.pojo.ChatCompletionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResultWithModel extends ChatCompletionResult {
    private String model;
}
