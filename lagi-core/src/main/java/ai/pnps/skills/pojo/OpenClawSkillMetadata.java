package ai.pnps.skills.pojo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OpenClawSkillMetadata {
    private final String primaryEnv;
    private final List<String> requiredEnv;
    private final List<String> requiredBins;
    private final List<String> requiredAnyBins;

    public OpenClawSkillMetadata(String primaryEnv,
                                 List<String> requiredEnv,
                                 List<String> requiredBins,
                                 List<String> requiredAnyBins) {
        this.primaryEnv = primaryEnv;
        this.requiredEnv = requiredEnv == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(requiredEnv));
        this.requiredBins = requiredBins == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(requiredBins));
        this.requiredAnyBins = requiredAnyBins == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(requiredAnyBins));
    }

    public String getPrimaryEnv() {
        return primaryEnv;
    }

    public List<String> getRequiredEnv() {
        return requiredEnv;
    }

    public List<String> getRequiredBins() {
        return requiredBins;
    }

    public List<String> getRequiredAnyBins() {
        return requiredAnyBins;
    }
}
