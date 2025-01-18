package ai.servlet.dto;

import ai.openai.pojo.ChatCompletionRequest;
import lombok.*;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VectorSearchRequest {
    private String category;
    private Map<String, String> where;
    private String text;
}
