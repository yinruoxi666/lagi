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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OpenClaw Context Compression API.
 *
 * Endpoint:
 * POST /v1/openclaw/compress
 */
public class OpenClawApiServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(OpenClawApiServlet.class);
    private static final int KEEP_RECENT_MESSAGES = 8;
    private static final int MAX_PREVIEW_CHARS = 200;
    private static final int DEFAULT_TARGET_REDUCTION_CHARS = 1200;
    private static final int MIN_TARGET_CHARS = 1200;
    private static final int NORMAL_COMPRESSED_CHARS = 220;
    private static final int AGGRESSIVE_COMPRESSED_CHARS = 120;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");

        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if ("compress".equals(method)) {
            compress(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responsePrint(resp, gson.toJson(errorResponse("Unknown endpoint: " + method)));
        }
    }

    private void compress(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CompressRequest request = reqBodyToObj(req, CompressRequest.class);

        if (request == null || request.getMessages() == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responsePrint(resp, gson.toJson(errorResponse("Request body or messages field is missing")));
            return;
        }

        logger.info("[OpenClaw] /compress called");
        logger.info("[OpenClaw] sessionId      = {}", request.getSessionId());
        logger.info("[OpenClaw] tokenBudget    = {}", request.getTokenBudget());
        logger.info("[OpenClaw] currentTokens  = {}", request.getCurrentTokenCount());
        logger.info("[OpenClaw] messageCount   = {}", request.getMessages().size());

        List<CompressMessage> messages = request.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            CompressMessage msg = messages.get(i);
            String contentPreview = msg.getContent() != null
                    ? String.valueOf(msg.getContent())
                    : "null";
            if (contentPreview.length() > MAX_PREVIEW_CHARS) {
                contentPreview = contentPreview.substring(0, MAX_PREVIEW_CHARS) + "... [truncated]";
            }
            logger.info("[OpenClaw] message[{}] id={} role={} content={}",
                    i, msg.getId(), msg.getRole(), contentPreview);
        }

        int tokensBefore = estimateTokens(messages, request.getCurrentTokenCount());
        List<CompressMessage> compressedMessages = compressMessages(messages, request.getTokenBudget(), tokensBefore);
        int tokensAfter = estimateTokens(compressedMessages, null);

        logger.info("[OpenClaw] compression result: tokensBefore={} tokensAfter={} changed={}",
                tokensBefore,
                tokensAfter,
                !sameContent(messages, compressedMessages));

        CompressResponse response = new CompressResponse();
        response.setStatus("success");
        response.setMessages(compressedMessages);
        response.setTokensBefore(tokensBefore);
        response.setTokensAfter(tokensAfter);

        responsePrint(resp, gson.toJson(response));
    }

    private List<CompressMessage> compressMessages(List<CompressMessage> originalMessages,
                                                   Integer tokenBudget,
                                                   int tokensBefore) {
        if (originalMessages.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompressMessage> result = cloneMessages(originalMessages);
        int totalChars = totalChars(result);
        int targetChars = resolveTargetChars(totalChars, tokenBudget, tokensBefore);
        if (totalChars <= targetChars) {
            return result;
        }

        int keepStart = Math.max(0, result.size() - KEEP_RECENT_MESSAGES);
        reduceMessages(result, 0, keepStart, targetChars, NORMAL_COMPRESSED_CHARS);

        if (totalChars(result) > targetChars) {
            reduceMessages(result, 0, keepStart, targetChars, AGGRESSIVE_COMPRESSED_CHARS);
        }

        return result;
    }

    private void reduceMessages(List<CompressMessage> messages,
                                int startInclusive,
                                int endExclusive,
                                int targetChars,
                                int maxCompressedChars) {
        for (int i = startInclusive; i < endExclusive && totalChars(messages) > targetChars; i++) {
            CompressMessage current = messages.get(i);
            if (!isCompressible(current)) {
                continue;
            }

            String originalText = extractContentText(current.getContent());
            String compressedText = summarizeMessage(current, originalText, maxCompressedChars);
            if (compressedText.equals(originalText)) {
                continue;
            }

            CompressMessage replacement = copyMessage(current);
            replacement.setContent(compressedText);
            replacement.setCompressed(Boolean.TRUE);
            messages.set(i, replacement);
        }
    }

    private boolean isCompressible(CompressMessage message) {
        if (message == null) {
            return false;
        }
        if ("system".equals(message.getRole())) {
            return false;
        }
        return extractContentText(message.getContent()).length() > AGGRESSIVE_COMPRESSED_CHARS;
    }

    private int resolveTargetChars(int totalChars, Integer tokenBudget, int tokensBefore) {
        int budgetChars = tokenBudget != null && tokenBudget > 0 ? tokenBudget * 4 : Integer.MAX_VALUE;
        int fallbackTarget = totalChars - Math.max(DEFAULT_TARGET_REDUCTION_CHARS, totalChars / 3);
        int currentChars = Math.max(tokensBefore, 0) * 4;
        if (currentChars > 0) {
            fallbackTarget = Math.min(
                    fallbackTarget,
                    Math.max(MIN_TARGET_CHARS, currentChars - DEFAULT_TARGET_REDUCTION_CHARS)
            );
        }
        int target = Math.min(budgetChars, fallbackTarget);
        return Math.max(MIN_TARGET_CHARS, target);
    }

    private List<CompressMessage> cloneMessages(List<CompressMessage> source) {
        List<CompressMessage> copies = new ArrayList<>(source.size());
        for (CompressMessage message : source) {
            copies.add(copyMessage(message));
        }
        return copies;
    }

    private CompressMessage copyMessage(CompressMessage source) {
        CompressMessage copy = new CompressMessage();
        copy.setId(source.getId());
        copy.setRole(source.getRole());
        copy.setContent(source.getContent());
        copy.setTimestamp(source.getTimestamp());
        copy.setName(source.getName());
        copy.setTool_call_id(source.getTool_call_id());
        copy.setMetadata(source.getMetadata());
        copy.setCompressed(source.getCompressed());
        return copy;
    }

    private int estimateTokens(List<CompressMessage> messages, Integer fallbackTokens) {
        int chars = totalChars(messages);
        if (chars <= 0 && fallbackTokens != null) {
            return fallbackTokens;
        }
        return Math.max(1, (int) Math.ceil(chars / 4.0d));
    }

    private int totalChars(List<CompressMessage> messages) {
        int sum = 0;
        for (CompressMessage message : messages) {
            sum += extractContentText(message.getContent()).length();
        }
        return sum;
    }

    private String summarizeMessage(CompressMessage message, String originalText, int maxChars) {
        String normalized = normalizeWhitespace(originalText);
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        String role = message.getRole() == null ? "message" : message.getRole();
        String prefix = "[LinkMind compressed " + role + " message] ";
        int bodyBudget = Math.max(40, maxChars - prefix.length());

        String summaryBody;
        if ("tool".equals(role) || "toolResult".equals(role)) {
            summaryBody = truncateMiddle(normalized, bodyBudget);
        } else {
            int head = Math.max(24, bodyBudget * 2 / 3);
            int tail = Math.max(12, bodyBudget - head - 5);
            summaryBody = normalized.substring(0, Math.min(head, normalized.length()));
            if (normalized.length() > head + tail) {
                summaryBody = summaryBody + " ... " + normalized.substring(normalized.length() - tail);
            }
        }

        return prefix + truncateMiddle(summaryBody, bodyBudget);
    }

    private String truncateMiddle(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 10) {
            return text.substring(0, Math.max(0, maxChars));
        }
        int head = Math.max(4, maxChars / 2 - 3);
        int tail = Math.max(3, maxChars - head - 5);
        return text.substring(0, Math.min(head, text.length())) + " ... "
                + text.substring(Math.max(head, text.length() - tail));
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    private String extractContentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return normalizeWhitespace((String) content);
        }
        if (content instanceof Number || content instanceof Boolean) {
            return String.valueOf(content);
        }
        if (content instanceof Map) {
            Object text = ((Map<?, ?>) content).get("text");
            if (text != null) {
                return extractContentText(text);
            }
            StringBuilder builder = new StringBuilder();
            for (Object value : ((Map<?, ?>) content).values()) {
                appendContentText(builder, value);
            }
            return normalizeWhitespace(builder.toString());
        }
        if (content instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (Iterable<?>) content) {
                appendContentText(builder, item);
            }
            return normalizeWhitespace(builder.toString());
        }
        return normalizeWhitespace(String.valueOf(content));
    }

    private void appendContentText(StringBuilder builder, Object value) {
        String text = extractContentText(value);
        if (text.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(text);
    }

    private boolean sameContent(List<CompressMessage> left, List<CompressMessage> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            CompressMessage a = left.get(i);
            CompressMessage b = right.get(i);
            if (!stringEquals(a.getId(), b.getId())) {
                return false;
            }
            if (!stringEquals(extractContentText(a.getContent()), extractContentText(b.getContent()))) {
                return false;
            }
        }
        return true;
    }

    private boolean stringEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private Map<String, String> errorResponse(String message) {
        Map<String, String> map = new HashMap<>();
        map.put("status", "error");
        map.put("error", message);
        return map;
    }
}
