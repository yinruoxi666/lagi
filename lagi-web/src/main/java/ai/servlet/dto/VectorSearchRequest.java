package ai.servlet.dto;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VectorSearchRequest {
    private String category;
    private Map<String, Object> where;
    private String text;
    private List<ChatMessage> messages;
}
