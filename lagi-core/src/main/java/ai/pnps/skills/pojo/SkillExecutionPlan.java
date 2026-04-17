package ai.pnps.skills.pojo;

import ai.pnps.skills.util.SkillsJsons;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@ToString
public final class SkillExecutionPlan {
    public enum Mode { SCRIPT, CHAT }

    @Getter
    @ToString
    public static final class Script {
        /**
         * OpenClaw-style argv (non-null and non-empty => use as command line, no implicit python).
         */
        private final List<String> argv;
        private final String path;
        private final List<String> args;
        private final String workingDir;

        /**
         * Legacy: {@code python path arg1 arg2 ...} under {@code workingDir}.
         */
        public Script(String path, List<String> args, String workingDir) {
            this.argv = null;
            this.path = path;
            this.args = args == null ? Collections.<String>emptyList() : args;
            this.workingDir = workingDir == null ? "." : workingDir;
        }

        /**
         * argv[0] is the executable or interpreter; further elements are arguments (paths relative to skill dir).
         */
        public Script(List<String> argv, String workingDir) {
            this.argv = argv == null ? Collections.<String>emptyList() : new ArrayList<String>(argv);
            this.path = null;
            this.args = Collections.emptyList();
            this.workingDir = workingDir == null ? "." : workingDir;
        }

        public boolean isArgvMode() {
            return argv != null && !argv.isEmpty();
        }

    }

    private final Mode mode;
    private final String chatResponse;
    private final Script script;
    private final String reasoning;

    public SkillExecutionPlan(Mode mode, String chatResponse, Script script, String reasoning) {
        this.mode = mode;
        this.chatResponse = chatResponse;
        this.script = script;
        this.reasoning = reasoning;
    }

    public static SkillExecutionPlan parse(String raw) {
        JsonNode node = SkillsJsons.parseObject(raw);
        if (node == null) {
            return new SkillExecutionPlan(Mode.CHAT, "执行计划解析失败。", null, "parse_error");
        }
        String modeRaw = text(node.get("mode"));
        Mode mode = "script".equalsIgnoreCase(modeRaw) ? Mode.SCRIPT : Mode.CHAT;
        JsonNode scriptNode = node.get("script");
        Script script = null;
        if (mode == Mode.SCRIPT && scriptNode != null && scriptNode.isObject()) {
            String wd = text(scriptNode.get("working_dir"));
            JsonNode argvNode = scriptNode.get("argv");
            if (argvNode != null && argvNode.isArray() && !argvNode.isEmpty()) {
                List<String> av = new ArrayList<String>();
                for (JsonNode a : argvNode) {
                    if (a != null && !a.isNull()) {
                        av.add(a.isTextual() ? a.asText() : a.toString());
                    }
                }
                if (!av.isEmpty()) {
                    script = new Script(av, wd);
                }
            }
            if (script == null) {
                String path = text(scriptNode.get("path"));
                List<String> args = new ArrayList<String>();
                JsonNode argsNode = scriptNode.get("args");
                if (argsNode != null && argsNode.isArray()) {
                    for (JsonNode a : argsNode) {
                        if (a != null && !a.isNull()) {
                            args.add(a.isTextual() ? a.asText() : a.toString());
                        }
                    }
                }
                script = new Script(path, args, wd);
            }
        }
        return new SkillExecutionPlan(mode, text(node.get("chat_response")), script, text(node.get("reasoning")));
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isTextual() ? n.asText() : n.toString();
    }
}
