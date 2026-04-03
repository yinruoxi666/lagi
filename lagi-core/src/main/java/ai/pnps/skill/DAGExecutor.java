package ai.pnps.skill;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Execute a skill DAG in order, injecting upstream outputs via env vars.
 * <p>
 * This is the Java-side counterpart of ms-agent {@code DAGExecutor}.
 */
public class DAGExecutor {

    private final SkillContainer container;
    private final Map<String, SkillSchema> skills;
    private final int maxRetries;

    public DAGExecutor(SkillContainer container, Map<String, SkillSchema> skills) {
        this(container, skills, 1);
    }

    public DAGExecutor(SkillContainer container, Map<String, SkillSchema> skills, int maxRetries) {
        this.container = Objects.requireNonNull(container, "container");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.maxRetries = maxRetries > 0 ? maxRetries : 1;
    }

    public DAGExecutionResult execute(Map<String, List<String>> dag,
                                       List<Object> executionOrder,
                                       ExecutionInput executionInput,
                                       boolean stopOnFailure) {
        long startNs = System.nanoTime();

        Map<String, ExecutionOutput> priorOutputs = new LinkedHashMap<>();
        Map<String, SkillExecutionResult> results = new LinkedHashMap<>();
        List<Object> actualOrder = new ArrayList<>();

        boolean allSuccess = true;
        if (executionOrder == null) {
            executionOrder = new ArrayList<Object>();
        }

        for (Object item : executionOrder) {
            if (item instanceof List) {
                // Parallel group: for simplicity, execute sequentially.
                @SuppressWarnings("unchecked")
                List<String> group = (List<String>) item;
                for (String sid : group) {
                    SkillExecutionResult r = executeSingle(sid, dag, executionInput, priorOutputs);
                    results.put(r.getSkillId(), r);
                    if (r.isSuccess()) {
                        priorOutputs.put(r.getSkillId(), r.getOutput());
                    } else {
                        allSuccess = false;
                        if (stopOnFailure) {
                            actualOrder.add(item);
                            DAGExecutionResult finalResult = new DAGExecutionResult();
                            finalResult.setSuccess(false);
                            finalResult.setResults(results);
                            finalResult.setExecutionOrder(actualOrder);
                            finalResult.setTotalDurationMs((System.nanoTime() - startNs) / 1_000_000.0);
                            return finalResult;
                        }
                    }
                }
                actualOrder.add(item);
            } else if (item instanceof String) {
                String sid = (String) item;
                SkillExecutionResult r = executeSingle(sid, dag, executionInput, priorOutputs);
                results.put(r.getSkillId(), r);
                if (r.isSuccess()) {
                    priorOutputs.put(r.getSkillId(), r.getOutput());
                } else {
                    allSuccess = false;
                    if (stopOnFailure) {
                        actualOrder.add(item);
                        DAGExecutionResult finalResult = new DAGExecutionResult();
                        finalResult.setSuccess(false);
                        finalResult.setResults(results);
                        finalResult.setExecutionOrder(actualOrder);
                        finalResult.setTotalDurationMs((System.nanoTime() - startNs) / 1_000_000.0);
                        return finalResult;
                    }
                }
                actualOrder.add(item);
            }
        }

        DAGExecutionResult finalResult = new DAGExecutionResult();
        finalResult.setSuccess(allSuccess);
        finalResult.setResults(results);
        finalResult.setExecutionOrder(actualOrder);
        finalResult.setTotalDurationMs((System.nanoTime() - startNs) / 1_000_000.0);
        return finalResult;
    }

    private SkillExecutionResult executeSingle(String skillId,
                                                Map<String, List<String>> dag,
                                                ExecutionInput executionInput,
                                                Map<String, ExecutionOutput> priorOutputs) {
        SkillSchema skill = skills.get(skillId);
        if (skill == null) {
            SkillExecutionResult r = new SkillExecutionResult();
            r.setSkillId(skillId);
            r.setSuccess(false);
            r.setError("Skill not found: " + skillId);
            r.setOutput(new ExecutionOutput());
            return r;
        }

        ExecutionInput execInput = SkillUpstreamEnv.buildExecutionInput(
                skillId, dag, executionInput, priorOutputs);

        ExecutionOutput output = null;
        String lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            SkillExecutionResult attemptRes = doExecute(skillId, skill, execInput);
            output = attemptRes.getOutput();
            lastError = attemptRes.getError();
            if (attemptRes.isSuccess()) {
                return attemptRes;
            }
        }

        SkillExecutionResult r = new SkillExecutionResult();
        r.setSkillId(skillId);
        r.setSuccess(false);
        r.setOutput(output != null ? output : new ExecutionOutput());
        r.setError(lastError != null ? lastError : "Execution failed");
        return r;
    }

    private SkillExecutionResult doExecute(String skillId, SkillSchema skill, ExecutionInput execInput) {
        SkillExecutionResult res = new SkillExecutionResult();
        res.setSkillId(skillId);

        try {
            if (skill.getScripts() != null && !skill.getScripts().isEmpty()) {
                SkillFile primary = skill.getScripts().get(0);
                String type = primary.getType() != null ? primary.getType().toLowerCase() : "";
                Path scriptPath = primary.getPath();
                if (".py".equals(type)) {
                    ExecutionOutput out = container.executePythonScript(scriptPath, skillId, execInput);
                    res.setOutput(out);
                    res.setSuccess(out.getExitCode() == 0);
                    res.setError(out.getExitCode() == 0 ? null : out.getStderr());
                    return res;
                }
                // For now: focus on python skills (ms-agent skill module currently treats .py/.sh/.js).
                ExecutionOutput out = new ExecutionOutput();
                out.setExitCode(-1);
                out.setStderr("Unsupported script type: " + type);
                res.setOutput(out);
                res.setSuccess(false);
                res.setError("Unsupported script type: " + type);
                return res;
            }

            // Fallback: run python code from SKILL.md content (best-effort).
            ExecutionOutput out = container.executePythonCode(skill.getContent(), skillId, execInput);
            res.setOutput(out);
            res.setSuccess(out.getExitCode() == 0);
            res.setError(out.getExitCode() == 0 ? null : out.getStderr());
            return res;
        } catch (Exception e) {
            ExecutionOutput out = new ExecutionOutput();
            out.setExitCode(-1);
            out.setStderr(e.getMessage());
            res.setOutput(out);
            res.setSuccess(false);
            res.setError(e.getMessage());
            return res;
        }
    }
}

