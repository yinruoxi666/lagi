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
public class ToolResultMessage implements AgentMessage {

    private final String role = "toolResult";

    private String toolCallId;

    private String toolName;

    /**
     * Content blocks: TextContent | ImageContent
     */
    private List<Content> content;

    private Object details;

    private boolean isError;

    private long timestamp;
}
