package ai.llm.pojo;

import ai.openai.pojo.ChatCompletionRequest;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ArvryuyiChatCompletionRequest extends ChatCompletionRequest {
    private String sn;
}
