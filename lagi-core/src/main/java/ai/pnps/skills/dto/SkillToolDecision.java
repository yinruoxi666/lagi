package ai.pnps.skills.dto;

import ai.pnps.skills.util.SkillsJsons;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tool-loop decision from LLM.
 */
public final class SkillToolDecision {
    public enum Type { TOOL_CALL, FINAL }

    public static final class ToolArgs {
        private final List<String> argv;
        private final String path;
        private final List<String> args;
        private final String workingDir;

        public ToolArgs(List<String> argv, String path, List<String> args, String workingDir) {
            this.argv = argv == null ? Collections.<String>emptyList() : argv;
            this.path = path;
            this.args = args == null ? Collections.<String>emptyList() : args;
            this.workingDir = workingDir == null ? "." : workingDir;
        }

        public List<String> getArgv() {
            return argv;
        }

        public String getPath() {
            return path;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getWorkingDir() {
            return workingDir;
        }
    }

    private final Type type;
    private final String toolName;
    private final ToolArgs arguments;
    private final String finalResponse;
    private final String reasoning;

    public SkillToolDecision(Type type, String toolName, ToolArgs arguments, String finalResponse, String reasoning) {
        this.type = type;
        this.toolName = toolName;
        this.arguments = arguments;
        this.finalResponse = finalResponse;
        this.reasoning = reasoning;
    }

    public Type getType() {
        return type;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolArgs getArguments() {
        return arguments;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public String getReasoning() {
        return reasoning;
    }

    public static SkillToolDecision parse(String raw) {
        JsonNode node = SkillsJsons.parseObject(raw);
        if (node == null) {
            return new SkillToolDecision(Type.FINAL, null, null, "解析失败，请重试。", "parse_error");
        }
        String typeRaw = text(node.get("type"));
        Type type = "tool_call".equalsIgnoreCase(typeRaw) ? Type.TOOL_CALL : Type.FINAL;
        String toolName = text(node.get("tool_name"));
        JsonNode argsNode = node.get("arguments");
        ToolArgs parsedArgs = null;
        if (argsNode != null && argsNode.isObject()) {
            String path = text(argsNode.get("path"));
            String wd = text(argsNode.get("working_dir"));
            List<String> argv = new ArrayList<String>();
            JsonNode argvNode = argsNode.get("argv");
            if (argvNode != null && argvNode.isArray()) {
                for (JsonNode item : argvNode) {
                    if (item != null && !item.isNull()) {
                        argv.add(item.isTextual() ? item.asText() : item.toString());
                    }
                }
            }
            List<String> args = new ArrayList<String>();
            JsonNode list = argsNode.get("args");
            if (list != null && list.isArray()) {
                for (JsonNode item : list) {
                    if (item != null && !item.isNull()) {
                        args.add(item.isTextual() ? item.asText() : item.toString());
                    }
                }
            }
            parsedArgs = new ToolArgs(argv, path, args, wd);
        }
        return new SkillToolDecision(
                type,
                toolName,
                parsedArgs,
                text(node.get("final_response")),
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
