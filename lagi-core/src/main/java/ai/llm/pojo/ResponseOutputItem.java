package ai.llm.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ResponseOutputItem {

    private String type;   // "message" 等
    private String id;
    private String status; // "completed" 等
    private String role;   // "assistant" / "user" 等

    private List<OutputContentItem> content;
}
