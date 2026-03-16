package ai.servlet.api;

import ai.dto.linkmind.CompressMessage;
import ai.dto.linkmind.CompressRequest;
import ai.dto.linkmind.CompressResponse;
import ai.servlet.BaseServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenClaw Context Compression API
 *
 * Endpoints:
 *   POST /v1/openclaw/compress   — compress conversation history
 */
public class OpenClawApiServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(OpenClawApiServlet.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");

        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if ("compress".equals(method)) {
            this.compress(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responsePrint(resp, gson.toJson(errorResponse("Unknown endpoint: " + method)));
        }
    }

    /**
     * POST /v1/openclaw/compress
     *
     * Accepts the full conversation history from the OpenClaw plugin and returns
     * a compressed version. The compression logic is intentionally left as a stub
     * for now — this handler prints the incoming data so we can observe the shape
     * of real messages before designing the compression algorithm.
     */
    private void compress(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // --- 1. Parse request ---
        CompressRequest request = reqBodyToObj(req, CompressRequest.class);

        if (request == null || request.getMessages() == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responsePrint(resp, gson.toJson(errorResponse("Request body or messages field is missing")));
            return;
        }

        // --- 2. Log the incoming data so we can inspect the real message format ---
        logger.info("[OpenClaw] /compress called");
        logger.info("[OpenClaw] sessionId      = {}", request.getSessionId());
        logger.info("[OpenClaw] tokenBudget    = {}", request.getTokenBudget());
        logger.info("[OpenClaw] currentTokens  = {}", request.getCurrentTokenCount());
        logger.info("[OpenClaw] messageCount   = {}", request.getMessages().size());

        List<CompressMessage> messages = request.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            CompressMessage msg = messages.get(i);
            String contentPreview = msg.getContent() != null
                    ? msg.getContent().toString()
                    : "null";
            // Truncate long content in the log to keep it readable
            if (contentPreview.length() > 200) {
                contentPreview = contentPreview.substring(0, 200) + "... [truncated]";
            }
            logger.info("[OpenClaw] message[{}] role={} content={}", i, msg.getRole(), contentPreview);
        }

        // --- 3. Stub: return original messages unchanged ---
        // TODO: replace with real LinkMind compression logic
        int tokensBefore = request.getCurrentTokenCount() != null ? request.getCurrentTokenCount() : 0;

        CompressResponse response = new CompressResponse();
        response.setStatus("success");
        response.setMessages(new ArrayList<>(messages)); // return as-is for now
        response.setTokensBefore(tokensBefore);
        response.setTokensAfter(tokensBefore);           // no actual compression yet

        responsePrint(resp, gson.toJson(response));
    }

    private java.util.Map<String, String> errorResponse(String message) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("status", "error");
        map.put("error", message);
        return map;
    }
}
