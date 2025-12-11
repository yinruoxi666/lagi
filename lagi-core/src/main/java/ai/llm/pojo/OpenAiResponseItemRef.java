package ai.llm.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiResponseItemRef implements OpenAiResponseInputItem{

    @JsonProperty("type")
    private String type; // 比如 "input_item_ref"

    @JsonProperty("id")
    private String id;

}
