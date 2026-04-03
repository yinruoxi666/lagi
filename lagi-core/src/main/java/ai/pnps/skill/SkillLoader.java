package ai.pnps.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 加载并管理 Skill 目录（对齐 ms-agent {@code SkillLoader} / {@code load_skills}）。
 * <p>
 * 不支持从 ModelScope Hub 自动下载；若传入 {@code owner/repo} 形式且路径不存在，将记录警告并跳过。
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private final Map<String, SkillSchema> loadedSkills = new LinkedHashMap<>();

    /**
     * @param skills 单个 Skill 根路径、多个 Skill 的父目录、多个目录列表，或已构造的 {@link SkillSchema} 列表
     */
    public Map<String, SkillSchema> loadSkills(List<?> skills) {
        Map<String, SkillSchema> all = new LinkedHashMap<>();
        if (skills == null || skills.isEmpty()) {
            log.warn("No skills provided to load.");
            return all;
        }
        for (Object skill : skills) {
            if (skill instanceof SkillSchema) {
                SkillSchema s = (SkillSchema) skill;
                String key = skillKey(s);
                all.put(key, s);
                log.info("Loaded skill from SkillSchema object: {}", key);
                continue;
            }
            if (!(skill instanceof String)) {
                throw new IllegalArgumentException("Invalid skills list element type: " + skill.getClass());
            }
            String pathStr = (String) skill;
            if (isLikelyRemoteSkillId(pathStr)) {
                if (!Files.exists(Paths.get(pathStr))) {
                    log.warn("ModelScope-style skill id [{}] is not supported for auto-download in Java; skip.", pathStr);
                }
                continue;
            }
            Path skillDir = Paths.get(pathStr);
            if (!Files.exists(skillDir)) {
                log.warn("Path does not exist: {} - Skipping.", skillDir);
                continue;
            }
            if (isSkillDirectory(skillDir)) {
                Optional<SkillSchema> parsed = SkillSchemaParser.parseSkillDirectory(skillDir);
                if (parsed.isPresent()) {
                    SkillSchema schema = parsed.get();
                    String key = skillKey(schema);
                    warnValidation(schema);
                    all.put(key, schema);
                }
            } else {
                all.putAll(scanAndLoadSkills(skillDir));
            }
        }
        loadedSkills.putAll(all);
        return all;
    }

    public Map<String, SkillSchema> loadSkills(String singlePath) {
        return loadSkills(Collections.singletonList(singlePath));
    }

    private static void warnValidation(SkillSchema schema) {
        List<String> errors = SkillSchemaParser.validateSkillSchema(schema);
        if (!errors.isEmpty()) {
            log.warn("Skill validation warnings ({}):", schema.getSkillPath());
            for (String e : errors) {
                log.warn("  - {}", e);
            }
        }
    }

    public static boolean isSkillDirectory(Path path) {
        return Files.isRegularFile(path.resolve("SKILL.md"));
    }

    private static boolean isLikelyRemoteSkillId(String s) {
        if (s == null || !s.contains("/")) {
            return false;
        }
        String[] parts = s.split("/");
        return parts.length == 2 && parts[0].length() > 0 && parts[1].length() > 0
                && !Files.exists(Paths.get(s));
    }

    private Map<String, SkillSchema> scanAndLoadSkills(Path basePath) {
        Map<String, SkillSchema> skills = new LinkedHashMap<>();
        if (!Files.isDirectory(basePath)) {
            log.warn("Not a valid directory: {}", basePath);
            return skills;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(basePath)) {
            for (Path item : stream) {
                if (Files.isDirectory(item) && isSkillDirectory(item)) {
                    Optional<SkillSchema> opt = SkillSchemaParser.parseSkillDirectory(item);
                    if (opt.isPresent()) {
                        SkillSchema s = opt.get();
                        warnValidation(s);
                        skills.put(skillKey(s), s);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error scanning skills under {}: {}", basePath, e.getMessage(), e);
        }
        return skills;
    }

    public static String skillKey(SkillSchema skill) {
        return skill.getSkillId() + "@" + skill.getVersion();
    }

    public SkillSchema getSkill(String skillKey) {
        return loadedSkills.get(skillKey);
    }

    public List<String> listSkills() {
        return new ArrayList<>(loadedSkills.keySet());
    }

    public Map<String, SkillSchema> getAllSkills() {
        return Collections.unmodifiableMap(loadedSkills);
    }

    public Optional<SkillSchema> reloadSkill(String skillPath) {
        Path pathObj = Paths.get(skillPath);
        if (!isSkillDirectory(pathObj)) {
            log.error("Not a valid skill directory: {}", skillPath);
            return Optional.empty();
        }
        Optional<SkillSchema> opt = SkillSchemaParser.parseSkillDirectory(pathObj);
        if (opt.isPresent()) {
            SkillSchema s = opt.get();
            loadedSkills.put(skillKey(s), s);
            log.info("Successfully reloaded skill: {}", s.getName());
        }
        return opt;
    }

    /** 便捷方法：无需持有 {@link SkillLoader} 实例。 */
    public static Map<String, SkillSchema> loadSkillsStatic(List<?> skills) {
        return new SkillLoader().loadSkills(skills);
    }

    public static Map<String, SkillSchema> loadSkillsStatic(String singlePath) {
        return new SkillLoader().loadSkills(singlePath);
    }
}
