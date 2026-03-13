package ai.dto.linkmind;

import java.util.List;

/**
 * Request body for POST /v1/linkmind/compress
 *
 * Sent by the OpenClaw plugin when context size exceeds the compression threshold.
 */
public class CompressRequest {

    /** Session identifier from OpenClaw */
    private String sessionId;

    /**
     * Full conversation history to compress.
     * The plugin sends all current messages; the API returns a compressed subset.
     */
    private List<CompressMessage> messages;

    /**
     * Total token budget for the session (from OpenClaw tokenBudget).
     * The compressed result should ideally fit within this budget.
     */
    private Integer tokenBudget;

    /**
     * Estimated token count of the input messages before compression.
     * Calculated by the plugin as totalChars / 4.
     */
    private Integer currentTokenCount;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<CompressMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<CompressMessage> messages) {
        this.messages = messages;
    }

    public Integer getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(Integer tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public Integer getCurrentTokenCount() {
        return currentTokenCount;
    }

    public void setCurrentTokenCount(Integer currentTokenCount) {
        this.currentTokenCount = currentTokenCount;
    }
}
