package ai.pnps.skill;

import lombok.Getter;
import lombok.ToString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Skill 目录完整模式（对齐 ms-agent {@code SkillSchema}）。
 * <p>
 * {@code skillId} 为目录名；元数据来自 {@code SKILL.md} 的 YAML frontmatter。
 */
@Getter
@ToString
public final class SkillSchema {

    private final String skillId;
    private final String name;
    private final String description;
    /**
     * -- GETTER --
     * 完整 SKILL.md 原文（含 frontmatter）。
     */
    private final String content;
    private final List<SkillFile> files;
    private final String version;
    private final String author;
    private final List<String> tags;
    private final List<SkillFile> scripts;
    private final List<SkillFile> references;
    private final List<SkillFile> resources;
    private final Path skillPath;

    public SkillSchema(
            String skillId,
            String name,
            String description,
            String content,
            List<SkillFile> files,
            String version,
            String author,
            List<String> tags,
            List<SkillFile> scripts,
            List<SkillFile> references,
            List<SkillFile> resources,
            Path skillPath) {
        if (skillId == null || skillId.isEmpty()) {
            throw new IllegalArgumentException("Skill ID cannot be empty");
        }
        if (name == null || name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("Skill name must be 1-64 characters");
        }
        if (description == null || description.isEmpty() || description.length() > 1024) {
            throw new IllegalArgumentException("Skill description must be 1-1024 characters");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Skill must contain at least one file");
        }
        boolean hasSkillMd = false;
        for (SkillFile f : files) {
            if ("SKILL.md".equals(f.getName())) {
                hasSkillMd = true;
                break;
            }
        }
        if (!hasSkillMd) {
            throw new IllegalArgumentException("Skill must contain SKILL.md file");
        }
        this.skillId = skillId;
        this.name = name;
        this.description = description;
        this.content = content != null ? content : "";
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.version = version != null && !version.isEmpty() ? version : "latest";
        this.author = author;
        this.tags = tags != null ? Collections.unmodifiableList(new ArrayList<>(tags)) : Collections.emptyList();
        this.scripts = scripts != null ? Collections.unmodifiableList(new ArrayList<>(scripts)) : Collections.emptyList();
        this.references = references != null ? Collections.unmodifiableList(new ArrayList<>(references)) : Collections.emptyList();
        this.resources = resources != null ? Collections.unmodifiableList(new ArrayList<>(resources)) : Collections.emptyList();
        this.skillPath = Objects.requireNonNull(skillPath, "skillPath").toAbsolutePath().normalize();
    }

    /** 与 ms-agent 一致的键：{@code skillId@version} */
    public String skillKey() {
        return skillId + "@" + version;
    }

    public SkillFile getFileByName(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (SkillFile f : files) {
            if (fileName.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }
}
