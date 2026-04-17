package ai.pnps.skills.pojo;

import ai.openai.pojo.ChatCompletionResult;
import lombok.Getter;

@Getter
public final class SkillsAgentResult {
    private final boolean usedSkill;
    private final String selectedSkillKey;
    private final String reply;
    private final String reasoning;
    private final ScriptExecutionResult executionResult;
    private final ChatCompletionResult originalResult;

    public SkillsAgentResult(boolean usedSkill,
                             String selectedSkillKey,
                             String reply,
                             String reasoning,
                             ScriptExecutionResult executionResult, ChatCompletionResult originalResult) {
        this.usedSkill = usedSkill;
        this.selectedSkillKey = selectedSkillKey;
        this.reply = reply;
        this.reasoning = reasoning;
        this.executionResult = executionResult;
        this.originalResult = originalResult;
    }

}
