package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AgentsConfig {
    private Boolean enable = true;
    @JsonProperty("items")
    private List<AgentConfig> agents;
}
