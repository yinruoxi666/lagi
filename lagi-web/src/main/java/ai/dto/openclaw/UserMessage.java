package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Corresponds to TypeScript UserMessage.
 * The content field can be a plain string or a list of TextContent/ImageContent.
 * When deserializing from JSON, if content is a string, wrap it as a single-element list
 * with a TextContent, or use a custom deserializer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMessage implements AgentMessage {

    private final String role = "user";

    /**
     * Can be a String or List of Content (TextContent | ImageContent).
     * Use Object to accommodate both JSON shapes.
     */
    private Object content;

    private long timestamp;

    /**
     * Helper: get content as a string if it is a plain string.
     */
    @JsonIgnore
    public String getContentAsString() {
        return content instanceof String ? (String) content : null;
    }

    /**
     * Helper: get content as a list if it is a structured content array.
     */
    @SuppressWarnings("unchecked")
    @JsonIgnore
    public List<Content> getContentAsList() {
        return content instanceof List ? (List<Content>) content : null;
    }
}
