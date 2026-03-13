package ai.dto.linkmind;

/**
 * Represents a single message in the conversation history.
 * Mirrors the OpenClaw AgentMessage format.
 */
public class CompressMessage {

    /** Message role: "user", "assistant", "system", "tool", etc. */
    private String role;

    /**
     * Message content. May be a plain string or a JSON array of content blocks.
     * We accept it as Object to handle both cases; Gson will deserialize accordingly.
     */
    private Object content;

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

    @Override
    public String toString() {
        return "CompressMessage{role='" + role + "', content=" + content + "}";
    }
}
