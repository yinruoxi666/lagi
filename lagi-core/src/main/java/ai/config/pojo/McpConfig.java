package ai.config.pojo;

import ai.common.pojo.McpBackend;
import lombok.Data;

import java.util.List;

@Data
public class McpConfig {
    private Boolean enable = true;
    private String driver;
    private List<McpBackend> servers;
}
