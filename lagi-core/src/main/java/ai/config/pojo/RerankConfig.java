package ai.config.pojo;

import lombok.Data;
import lombok.ToString;

@Data
public class RerankConfig {
    private String adapter;
    private String workspaceId;
    @ToString.Exclude
    private String apiKey;
}
