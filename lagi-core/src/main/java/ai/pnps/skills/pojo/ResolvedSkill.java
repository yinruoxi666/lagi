package ai.pnps.skills.pojo;

import java.nio.file.Path;

public final class ResolvedSkill {
    private final String name;
    private final String description;
    private final Path filePath;
    private final Path baseDir;
    private final String source;

    public ResolvedSkill(String name, String description, Path filePath, Path baseDir, String source) {
        this.name = name;
        this.description = description;
        this.filePath = filePath;
        this.baseDir = baseDir;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Path getFilePath() {
        return filePath;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public String getSource() {
        return source;
    }
}
