package ai.pnps.skills.pojo;

import lombok.Getter;

@Getter
public final class SkillExposure {
    private final boolean includeInRuntimeRegistry;
    private final boolean includeInAvailableSkillsPrompt;
    private final boolean userInvocable;

    public SkillExposure(boolean includeInRuntimeRegistry, boolean includeInAvailableSkillsPrompt, boolean userInvocable) {
        this.includeInRuntimeRegistry = includeInRuntimeRegistry;
        this.includeInAvailableSkillsPrompt = includeInAvailableSkillsPrompt;
        this.userInvocable = userInvocable;
    }

}
