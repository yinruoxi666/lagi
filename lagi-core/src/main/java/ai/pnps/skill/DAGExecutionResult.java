package ai.pnps.skill;

import lombok.Data;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill DAG 的最终执行结果（对齐 ms-agent {@code DAGExecutionResult} 的核心字段）。
 */
@Data
@ToString
public class DAGExecutionResult {
    private boolean success;
    private Map<String, SkillExecutionResult> results = new LinkedHashMap<>();
    private List<Object> executionOrder = Collections.emptyList();
    private double totalDurationMs;

    public Map<String, SkillExecutionResult> getResults() {
        return Collections.unmodifiableMap(results);
    }

    public void setResults(Map<String, SkillExecutionResult> results) {
        this.results = results != null ? new LinkedHashMap<>(results) : new LinkedHashMap<>();
    }

    public void setExecutionOrder(List<Object> executionOrder) {
        this.executionOrder = executionOrder != null ? executionOrder : Collections.emptyList();
    }
}

