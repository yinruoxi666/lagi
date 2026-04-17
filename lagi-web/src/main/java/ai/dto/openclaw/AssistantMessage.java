package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantMessage implements AgentMessage {

    private final String role = "assistant";

    /**
     * Content blocks: TextContent | ThinkingContent | ToolCall
     */
    private List<Content> content;

    private String api;

    private String provider;

    private String model;

    private String responseId;

    private Usage usage;

    private StopReason stopReason;

    private String errorMessage;

    private long timestamp;
}
