package ai.pnps.skill;

import lombok.ToString;

/**
 * 单个 Skill 的执行结果（对齐 ms-agent {@code SkillExecutionResult} 的核心字段）。
 */
@ToString
public class SkillExecutionResult {
    private String skillId;
    private boolean success;
    private ExecutionOutput output;
    private String error;

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ExecutionOutput getOutput() {
        return output;
    }

    public void setOutput(ExecutionOutput output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

