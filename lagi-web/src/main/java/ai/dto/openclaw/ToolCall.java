package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall implements Content {

    private final String type = "toolCall";

    private String id;

    private String name;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    private String thoughtSignature;
}
