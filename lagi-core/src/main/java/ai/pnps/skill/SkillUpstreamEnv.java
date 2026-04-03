package ai.pnps.skill;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为下游 Skill 注入上游输出环境变量（对齐 ms-agent {@code DAGExecutor._build_execution_input}）。
 */
public final class SkillUpstreamEnv {

    private static final int STDOUT_SHORTCUT_MAX = 4096;
    private static final ObjectMapper JSON = new ObjectMapper();

    private SkillUpstreamEnv() {
    }

    /**
     * @param skillId      当前要执行的 skill 键（如 {@code my_tool@latest}）
     * @param dag          依赖图：{@code dag.get(A) = [B,C]} 表示 A 依赖 B、C（先执行 B、C）
     * @param baseInput    用户侧基础入参，可为 null（视为空）
     * @param priorOutputs 已执行完的 skillId → 输出
     */
    public static ExecutionInput buildExecutionInput(
            String skillId,
            Map<String, List<String>> dag,
            ExecutionInput baseInput,
            Map<String, ExecutionOutput> priorOutputs) {
        ExecutionInput input = baseInput != null ? baseInput.copy() : new ExecutionInput();
        Map<String, String> env = input.mutableEnvVars();
        List<String> dependencies = dag != null ? dag.get(skillId) : null;
        if (dependencies == null || dependencies.isEmpty() || priorOutputs == null || priorOutputs.isEmpty()) {
            return input;
        }
        Map<String, Object> upstreamData = new LinkedHashMap<>();
        for (String depId : dependencies) {
            ExecutionOutput depOut = priorOutputs.get(depId);
            if (depOut == null) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("stdout", depOut.getStdout());
            one.put("stderr", depOut.getStderr());
            one.put("return_value", depOut.getReturnValue());
            one.put("exit_code", depOut.getExitCode());
            Map<String, String> files = new LinkedHashMap<>();
            for (Map.Entry<String, Path> e : depOut.getOutputFiles().entrySet()) {
                files.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
            }
            one.put("output_files", files);
            upstreamData.put(depId, one);
        }
        if (upstreamData.isEmpty()) {
            return input;
        }
        try {
            env.put("UPSTREAM_OUTPUTS", JSON.writeValueAsString(upstreamData));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize UPSTREAM_OUTPUTS", e);
        }
        for (Map.Entry<String, Object> e : upstreamData.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) e.getValue();
            Object so = data.get("stdout");
            if (so instanceof String && !((String) so).isEmpty()) {
                String s = (String) so;
                String safeKey = sanitizeSkillIdForEnv(e.getKey());
                env.put("UPSTREAM_" + safeKey + "_STDOUT",
                        s.length() > STDOUT_SHORTCUT_MAX ? s.substring(0, STDOUT_SHORTCUT_MAX) : s);
            }
        }
        return input;
    }

    /**
     * 与 Python 一致：{@code - . @ /} → {@code _} 并转大写。
     */
    public static String sanitizeSkillIdForEnv(String skillId) {
        if (skillId == null) {
            return "";
        }
        return skillId.replace('-', '_').replace('.', '_').replace('@', '_').replace('/', '_').toUpperCase();
    }
}
