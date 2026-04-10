package ai.pnps.skills.util;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SkillsJsons {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkillsJsons() {
    }

    public static JsonNode parseObject(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }


    public static String getArg(String arguments, String key) {
        JSONObject o = JSONUtil.parseObj(arguments);
        if ("path".equals(key)) {
            String p = o.getStr("path");
            if (p != null && !p.trim().isEmpty()) {
                return p;
            }
            return o.getStr("file_path");
        }
        return o.getStr(key);
    }
}
