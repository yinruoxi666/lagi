package ai.pnps.skill;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 单个 Skill 目录内的文件描述（对齐 ms-agent {@code SkillFile}）。
 */
@Getter
public final class SkillFile {

    private final String name;
    private final String type;
    private final Path path;
    private final boolean required;

    public SkillFile(String name, String type, Path path, boolean required) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("File type cannot be empty");
        }
        this.name = name;
        this.type = type;
        this.path = Objects.requireNonNull(path, "path");
        this.required = required;
    }

}
