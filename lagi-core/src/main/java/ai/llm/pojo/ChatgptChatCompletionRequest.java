package ai.llm.pojo;

import ai.common.pojo.Response;
import ai.openai.pojo.ChatCompletionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatgptChatCompletionRequest extends ChatCompletionRequest {
    private ResponseFormat response_format;

}
