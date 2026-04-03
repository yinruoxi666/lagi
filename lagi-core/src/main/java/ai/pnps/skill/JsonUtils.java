package ai.pnps.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_BLOCK_JSON = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private JsonUtils() {
    }

    public static JsonNode parseJsonObject(String text) {
        if (text == null) {
            return null;
        }

        String s = text.trim();
        Matcher m = CODE_BLOCK_JSON.matcher(s);
        if (m.find()) {
            s = m.group(1).trim();
        }

        // Try direct parse
        try {
            JsonNode node = MAPPER.readTree(s);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (Exception ignore) {
            // continue
        }

        // Fallback: extract outermost {...} by brace depth
        int start = s.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String jsonStr = s.substring(start, i + 1);
                    try {
                        JsonNode node = MAPPER.readTree(jsonStr);
                        if (node != null && node.isObject()) {
                            return node;
                        }
                    } catch (Exception ignore2) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static List<Object> parseExecutionOrder(JsonNode executionOrderNode) {
        if (executionOrderNode == null || !executionOrderNode.isArray()) {
            return new ArrayList<>();
        }
        List<Object> order = new ArrayList<>();
        for (JsonNode item : executionOrderNode) {
            if (item == null) {
                continue;
            }
            if (item.isTextual()) {
                order.add(item.asText());
            } else if (item.isArray()) {
                List<String> group = new ArrayList<>();
                for (JsonNode sid : item) {
                    if (sid != null && sid.isTextual()) {
                        group.add(sid.asText());
                    }
                }
                order.add(group);
            }
        }
        return order;
    }
}

