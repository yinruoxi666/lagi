package ai.dto.linkmind;

import java.util.Map;

/**
 * Represents a single message in the conversation history.
 * Mirrors the OpenClaw AgentMessage format.
 */
public class CompressMessage {

    /** Transcript entry id used by OpenClaw for safe rewrite operations. */
    private String id;

    /** Message role: "user", "assistant", "system", "tool", etc. */
    private String role;

    /**
     * Message content. May be a plain string or a JSON array of content blocks.
     * We accept it as Object to handle both cases; Gson will deserialize accordingly.
     */
    private Object content;

    /** Optional client timestamp in epoch milliseconds. */
    private Long timestamp;

    /** Optional tool/function name. */
    private String name;

    /** Optional tool call id. */
    private String tool_call_id;

    /** Optional message metadata. */
    private Map<String, Object> metadata;

    /** Whether the message is already compressed/summarized. */
    private Boolean compressed;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTool_call_id() {
        return tool_call_id;
    }

    public void setTool_call_id(String tool_call_id) {
        this.tool_call_id = tool_call_id;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Boolean getCompressed() {
        return compressed;
    }

    public void setCompressed(Boolean compressed) {
        this.compressed = compressed;
    }

    @Override
    public String toString() {
        return "CompressMessage{id='" + id + "', role='" + role + "', content=" + content + "}";
    }
}
