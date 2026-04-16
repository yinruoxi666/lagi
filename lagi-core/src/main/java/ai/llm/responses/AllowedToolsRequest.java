package ai.llm.responses;

import lombok.Data;

import java.util.List;

@Data
public class AllowedToolsRequest {
    private String mode;
    private List<Tool> tools;
    private String type;

    @Data
    public static class Tool {
        private String type;
        private String name;
    }
}