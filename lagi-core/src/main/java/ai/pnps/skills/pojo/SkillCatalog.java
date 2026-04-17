package ai.pnps.skills.pojo;

import java.util.Collections;
import java.util.List;

public final class SkillCatalog {
    private final String prompt;
    private final List<SkillEntry> entries;
    private final boolean compact;
    private final boolean truncated;

    public SkillCatalog(String prompt, List<SkillEntry> entries, boolean compact, boolean truncated) {
        this.prompt = prompt;
        this.entries = entries == null ? Collections.<SkillEntry>emptyList() : entries;
        this.compact = compact;
        this.truncated = truncated;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<SkillEntry> getEntries() {
        return entries;
    }

    public boolean isCompact() {
        return compact;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
