package ai.dto.linkmind;

import java.util.List;

/**
 * Response body for POST /v1/linkmind/compress
 */
public class CompressResponse {

    /** "success" or "error" */
    private String status;

    /**
     * Compressed messages to replace the original history.
     * OpenClaw plugin will write these back to the session file.
     */
    private List<CompressMessage> messages;

    /** Token count before compression (echoed from request for convenience) */
    private Integer tokensBefore;

    /** Estimated token count after compression (chars / 4) */
    private Integer tokensAfter;

    /** Optional error message when status is "error" */
    private String error;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CompressMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<CompressMessage> messages) {
        this.messages = messages;
    }

    public Integer getTokensBefore() {
        return tokensBefore;
    }

    public void setTokensBefore(Integer tokensBefore) {
        this.tokensBefore = tokensBefore;
    }

    public Integer getTokensAfter() {
        return tokensAfter;
    }

    public void setTokensAfter(Integer tokensAfter) {
        this.tokensAfter = tokensAfter;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
