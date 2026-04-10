package ai.pnps.skills.dto;

import ai.pnps.skills.util.SkillsJsons;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@Getter
public final class SkillSelection {
    private final boolean needsSkills;
    private final String chatResponse;
    private final String selectedSkillKey;
    private final String reasoning;

    public SkillSelection(boolean needsSkills, String chatResponse, String selectedSkillKey, String reasoning) {
        this.needsSkills = needsSkills;
        this.chatResponse = chatResponse;
        this.selectedSkillKey = selectedSkillKey;
        this.reasoning = reasoning;
    }

    public static SkillSelection parse(String raw) {
        JsonNode node = SkillsJsons.parseObject(raw);
        if (node == null) {
            return new SkillSelection(true, null, null, "parse_error");
        }
        return new SkillSelection(
                node.path("needs_skills").asBoolean(true),
                text(node.get("chat_response")),
                text(node.get("selected_skill_key")),
                text(node.get("reasoning"))
        );
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isTextual() ? n.asText() : n.toString();
    }
}
