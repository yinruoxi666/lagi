package ai.pnps.skills.util;

import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillExecutionPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SkillUtil {

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String firstNonBlank(String a, String b, String fallback) {
        String x = trimToNull(a);
        if (x != null) {
            return x;
        }
        String y = trimToNull(b);
        if (y != null) {
            return y;
        }
        return fallback == null ? "." : fallback;
    }

    public static SkillExecutionPlan.Script scriptFromProviderExecArgs(Map<String, String> args) {
        if (args == null) {
            return new SkillExecutionPlan.Script((String) null, null, null);
        }
        String command = trimToNull(args.get("command"));
        if (command != null) {
            String wd = firstNonBlank(args.get("workdir"), args.get("working_dir"), ".");
            return new SkillExecutionPlan.Script(shellArgvForCommand(command), wd);
        }
        List<String> argv = parseListArguments(args.get("argv"));
        if (!argv.isEmpty()) {
            String wd = firstNonBlank(args.get("working_dir"), args.get("workdir"), ".");
            return new SkillExecutionPlan.Script(argv, wd);
        }
        String legacyWd = firstNonBlank(args.get("working_dir"), args.get("workdir"), ".");
        return new SkillExecutionPlan.Script(
                args.get("path"),
                parseListArguments(args.get("args")),
                legacyWd);
    }

    /**
     * OpenClaw / provider-native exec: {@code command} is a shell string; run via {@code cmd /c} or {@code sh -c}.
     */
    private static List<String> shellArgvForCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Arrays.asList("cmd", "/c", command);
        }
        return Arrays.asList("sh", "-c", command);
    }

    private static List<String> parseListArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JsonNode n;
        try {
            n = new ObjectMapper().readTree(raw);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        if (n == null || !n.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : n) {
            if (item != null && !item.isNull()) {
                out.add(item.isTextual() ? item.asText() : item.toString());
            }
        }
        return out;
    }

    public static String formatExecToolResult(ScriptExecutionResult lastExec) {
        StringBuilder sb = new StringBuilder();
        sb.append("exit_code=").append(lastExec.getExitCode())
                .append(", timeout=").append(lastExec.isTimeout())
                .append(", no_output_timeout=").append(lastExec.isNoOutputTimeout())
                .append("\n");
        sb.append(lastExec.getOutput());
        return sb.toString();
    }
}
