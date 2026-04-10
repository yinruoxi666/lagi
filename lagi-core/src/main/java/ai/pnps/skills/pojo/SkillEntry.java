package ai.pnps.skills.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@ToString
@NoArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SkillEntry {
    private String key;
    private String name;
    private String description;
    private Path skillDir;
    private Path skillMdPath;
    private Map<String, String> frontmatter;
    private SkillInvocationPolicy invocation;
    private SkillExposure exposure;
    private OpenClawSkillMetadata metadata;
    private String source;
    private String rule;

    public SkillEntry(String key,
                      String name,
                      String description,
                      Path skillDir,
                      Path skillMdPath,
                      Map<String, String> frontmatter) {
        this(key, name, description, skillDir, skillMdPath, frontmatter, null, null, null, null,  null);
    }

    public SkillEntry(String key,
                      String name,
                      String description,
                      Path skillDir,
                      Path skillMdPath,
                      Map<String, String> frontmatter,
                      SkillInvocationPolicy invocation,
                      SkillExposure exposure,
                      OpenClawSkillMetadata metadata,
                      String source,
                      String rule) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.skillDir = skillDir;
        this.skillMdPath = skillMdPath;
        this.frontmatter = frontmatter == null ? Collections.<String, String>emptyMap() : frontmatter;
        this.invocation = invocation;
        this.exposure = exposure;
        this.metadata = metadata;
        this.source = source;
        this.rule = rule == null ? "server" : rule;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Path getSkillDir() {
        return skillDir;
    }

    public Path getSkillMdPath() {
        return skillMdPath;
    }

    public Map<String, String> getFrontmatter() {
        return frontmatter;
    }

    public SkillInvocationPolicy getInvocation() {
        return invocation;
    }

    public SkillExposure getExposure() {
        return exposure;
    }

    public OpenClawSkillMetadata getMetadata() {
        return metadata;
    }

    public String getSource() {
        return source;
    }
}
