package ai.pnps.skills.pojo;

import lombok.Getter;

@Getter
public final class SkillInvocationPolicy {
    private final boolean userInvocable;
    private final boolean disableModelInvocation;

    public SkillInvocationPolicy(boolean userInvocable, boolean disableModelInvocation) {
        this.userInvocable = userInvocable;
        this.disableModelInvocation = disableModelInvocation;
    }

}
