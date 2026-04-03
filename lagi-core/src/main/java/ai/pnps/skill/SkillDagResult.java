package ai.pnps.skill;

import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 选 Skill + 建 DAG 的结果占位（对齐 ms-agent {@code SkillDAGResult} 的核心字段）。
 * <p>
 * 实际「分析 query / 过滤 / 调 LLM」可在业务层实现，本模块只提供数据结构与加载、拓扑、环境注入能力。
 */
@ToString
public class SkillDagResult {

    private Map<String, List<String>> dag = new LinkedHashMap<>();
    private List<Object> executionOrder = Collections.emptyList();
    private Map<String, SkillSchema> selectedSkills = new LinkedHashMap<>();
    private boolean complete;
    private String clarification;
    private String chatResponse;
    private DAGExecutionResult executionResult;

    public Map<String, List<String>> getDag() {
        return Collections.unmodifiableMap(dag);
    }

    public void setDag(Map<String, List<String>> dag) {
        this.dag = dag != null ? new LinkedHashMap<>(dag) : new LinkedHashMap<>();
    }

    /**
     * 执行顺序：元素为 skillId 字符串，或表示并行的 {@code List<String>}（与 ms-agent 一致，供上层解释）。
     */
    public List<Object> getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(List<Object> executionOrder) {
        this.executionOrder = executionOrder != null ? executionOrder : Collections.emptyList();
    }

    public Map<String, SkillSchema> getSelectedSkills() {
        return Collections.unmodifiableMap(selectedSkills);
    }

    public void setSelectedSkills(Map<String, SkillSchema> selectedSkills) {
        this.selectedSkills = selectedSkills != null ? new LinkedHashMap<>(selectedSkills) : new LinkedHashMap<>();
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public String getClarification() {
        return clarification;
    }

    public void setClarification(String clarification) {
        this.clarification = clarification;
    }

    public String getChatResponse() {
        return chatResponse;
    }

    public void setChatResponse(String chatResponse) {
        this.chatResponse = chatResponse;
    }

    public DAGExecutionResult getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(DAGExecutionResult executionResult) {
        this.executionResult = executionResult;
    }
}
