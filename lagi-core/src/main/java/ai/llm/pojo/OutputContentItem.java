package ai.llm.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OutputContentItem {

    private String type; // "output_text" 等
    private String text;
}
