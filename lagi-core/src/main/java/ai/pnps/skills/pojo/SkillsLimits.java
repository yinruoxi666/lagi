package ai.pnps.skills.pojo;

import lombok.Getter;

@Getter
public final class SkillsLimits {
    private final int maxCandidatesPerRoot;
    private final int maxSkillsLoadedPerSource;
    private final int maxSkillsInPrompt;
    private final int maxSkillsPromptChars;
    private final long maxSkillFileBytes;

    public SkillsLimits(int maxCandidatesPerRoot,
                        int maxSkillsLoadedPerSource,
                        int maxSkillsInPrompt,
                        int maxSkillsPromptChars,
                        long maxSkillFileBytes) {
        this.maxCandidatesPerRoot = maxCandidatesPerRoot;
        this.maxSkillsLoadedPerSource = maxSkillsLoadedPerSource;
        this.maxSkillsInPrompt = maxSkillsInPrompt;
        this.maxSkillsPromptChars = maxSkillsPromptChars;
        this.maxSkillFileBytes = maxSkillFileBytes;
    }

}
