package ai.pnps.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Skill 目录解析 {@link SkillSchema}（对齐 ms-agent {@code SkillSchemaParser}）。
 */
public final class SkillSchemaParser {

    private static final Logger log = LoggerFactory.getLogger(SkillSchemaParser.class);

    private static final Set<String> IGNORED_NAMES = new HashSet<String>() {{
        add(".DS_Store");
        add("__pycache__");
        add(".git");
        add(".gitignore");
        add(".pytest_cache");
        add(".mypy_cache");
    }};

    private static final Set<String> IGNORED_SUFFIXES = new HashSet<String>() {{
        add(".pyc");
        add(".pyo");
    }};

    private static final Set<String> SCRIPT_EXT = new HashSet<String>() {{
        add(".py");
        add(".sh");
        add(".js");
    }};

    /** 兼容末尾无换行的 closing {@code ---} */
    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\R([\\s\\S]*?)\\R---\\s*(?:\\R|$)");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private SkillSchemaParser() {
    }

    public static boolean isIgnoredPath(Path p) {
        String name = p.getFileName() != null ? p.getFileName().toString() : "";
        if (IGNORED_NAMES.contains(name)) {
            return true;
        }
        String suffix = extensionLower(name);
        return IGNORED_SUFFIXES.contains(suffix);
    }

    /**
     * 解析 YAML frontmatter（SKILL.md 顶部 {@code ---} ... {@code ---}）。
     */
    public static Optional<Map<String, Object>> parseYamlFrontmatter(String markdownContent) {
        if (markdownContent == null) {
            return Optional.empty();
        }
        Matcher m = FRONTMATTER.matcher(markdownContent);
        if (!m.find()) {
            return Optional.empty();
        }
        String yamlBlock = m.group(1).trim();
        if (yamlBlock.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> map = YAML.readValue(yamlBlock, new TypeReference<Map<String, Object>>() {});
            return Optional.ofNullable(map);
        } catch (IOException e) {
            log.warn("Failed to parse YAML frontmatter: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析单个 Skill 目录；无效时返回 {@link Optional#empty()}。
     */
    public static Optional<SkillSchema> parseSkillDirectory(Path directoryPath) {
        if (directoryPath == null || !Files.isDirectory(directoryPath)) {
            return Optional.empty();
        }
        Path skillMd = directoryPath.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            return Optional.empty();
        }
        String content;
        try {
            content = new String(Files.readAllBytes(skillMd), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read SKILL.md: {}", skillMd, e);
            return Optional.empty();
        }
        Optional<Map<String, Object>> fm = parseYamlFrontmatter(content);
        if (!fm.isPresent()) {
            return Optional.empty();
        }
        Map<String, Object> front = fm.get();
        Object nameObj = front.get("name");
        Object descObj = front.get("description");
        if (!(nameObj instanceof String) || !(descObj instanceof String)) {
            return Optional.empty();
        }
        String name = (String) nameObj;
        String description = (String) descObj;
        String version = stringOrNull(front.get("version"));
        if (version == null) {
            version = "latest";
        }
        String author = stringOrNull(front.get("author"));
        List<String> tags = parseTags(front.get("tags"));

        String skillId = directoryPath.getFileName().toString();

        List<Path> filePaths = new ArrayList<>();
        try {
            Files.walk(directoryPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p))
                    .forEach(filePaths::add);
        } catch (IOException e) {
            log.error("Failed to walk skill directory: {}", directoryPath, e);
            return Optional.empty();
        }
        filePaths.sort(Comparator.comparing(Path::toString));

        List<SkillFile> files = new ArrayList<>();
        List<SkillFile> scripts = new ArrayList<>();
        List<SkillFile> references = new ArrayList<>();
        List<SkillFile> resources = new ArrayList<>();

        for (Path filePath : filePaths) {
            String fname = filePath.getFileName().toString();
            String ext = extensionLower(fname);
            if (ext.isEmpty()) {
                ext = ".unknown";
            }
            boolean required = "SKILL.md".equals(fname);
            SkillFile sf = new SkillFile(fname, ext, filePath.toAbsolutePath().normalize(), required);
            files.add(sf);
            if (SCRIPT_EXT.contains(ext)) {
                scripts.add(sf);
            } else if (".md".equals(ext) && !"SKILL.md".equals(fname)) {
                references.add(sf);
            } else {
                resources.add(sf);
            }
        }

        try {
            return Optional.of(new SkillSchema(
                    skillId,
                    name,
                    description,
                    content,
                    files,
                    version,
                    author,
                    tags,
                    scripts,
                    references,
                    resources,
                    directoryPath));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid skill schema in {}: {}", directoryPath, e.getMessage());
            return Optional.empty();
        }
    }

    public static List<String> validateSkillSchema(SkillSchema schema) {
        if (schema == null) {
            return Collections.singletonList("schema is null");
        }
        List<String> errors = new ArrayList<>();
        if (schema.getSkillId() == null || schema.getSkillId().isEmpty()) {
            errors.add("Skill ID is required");
        }
        if (schema.getName() != null && schema.getName().length() > 64) {
            errors.add("Skill name exceeds 64 characters");
        }
        if (schema.getDescription() != null && schema.getDescription().length() > 1024) {
            errors.add("Skill description exceeds 1024 characters");
        }
        boolean hasSkillMd = false;
        for (SkillFile f : schema.getFiles()) {
            if ("SKILL.md".equals(f.getName())) {
                hasSkillMd = true;
                break;
            }
        }
        if (!hasSkillMd) {
            errors.add("SKILL.md is required");
        }
        if (schema.getSkillPath() == null || !Files.isDirectory(schema.getSkillPath())) {
            errors.add("Skill directory does not exist: " + schema.getSkillPath());
        }
        return errors;
    }

    private static String stringOrNull(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static List<String> parseTags(Object tagsObj) {
        if (tagsObj == null) {
            return Collections.emptyList();
        }
        if (tagsObj instanceof List) {
            List<?> raw = (List<?>) tagsObj;
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return Collections.singletonList(String.valueOf(tagsObj));
    }

    private static String extensionLower(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
