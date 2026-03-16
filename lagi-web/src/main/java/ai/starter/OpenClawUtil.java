package ai.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class OpenClawUtil {

    private static final String OPEN_CLAW_DIR_NAME = ".openclaw";
    private static final String[] MODELS_JSON_PATH_SEGMENTS = {"agents", "main", "agent", "models.json"};
    private static final String YAML_MODELS_NODE = "models:";
    private static final String YAML_INDENT = "  ";
    private static final String YAML_COMMENT_PREFIX = "#";
    private static final String CONFIG_FILE_PROPERTY = Application.CONFIG_FILE_PROPERTY;
    /**
     * Synchronize configurations for OpenClaw application.
     */
    public static void sync() {
        try {
            Path openClawDir = getOpenClawDir();
            if (openClawDir == null) {
                return;
            }

            JsonNode providersNode = readAndParseModelsJson(openClawDir);
            if (providersNode == null) {
                return;
            }

            Path lagiYmlPath = getLagiYmlPath();
            if (lagiYmlPath == null || !Files.exists(lagiYmlPath)) {
                return;
            }
            String originalYaml = readFileContent(lagiYmlPath);

            String updatedYaml = injectModelsIntoYaml(originalYaml, providersNode);
            if (originalYaml.equals(updatedYaml)) {
                return;
            }

            writeFileAtomic(lagiYmlPath, updatedYaml);

        } catch (Exception e) {
            log.error("Failed to sync OpenClaw config", e);
        }
    }


    /**
     * Get the root directory of OpenClaw.
     * @return Path of OpenClaw directory, or null if it does not exist
     */
    private static Path getOpenClawDir() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            return null;
        }

        Path openClawDir = Paths.get(userHome, OPEN_CLAW_DIR_NAME);
        return Files.isDirectory(openClawDir) ? openClawDir : null;
    }

    /**
     * Read and parse models.json, and extract the "providers" node.
     * @param openClawDir OpenClaw root directory
     * @return providers node, or null if invalid
     * @throws IOException read/parse error
     */
    private static JsonNode readAndParseModelsJson(Path openClawDir) throws IOException {
        Path modelsJsonPath = openClawDir;
        for (String segment : MODELS_JSON_PATH_SEGMENTS) {
            modelsJsonPath = modelsJsonPath.resolve(segment);
        }

        if (!Files.exists(modelsJsonPath)) {
            return null;
        }

        String content = readFileContent(modelsJsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode providersNode = root.get("providers");

        return (providersNode != null && providersNode.isObject()) ? providersNode : null;
    }

    /**
     * Get the path of lagi.yml configuration file.
     * @return config file path, or null if not configured
     */
    private static Path getLagiYmlPath() {
        String configFilePath = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFilePath == null || configFilePath.trim().isEmpty()) {
            return null;
        }
        return Paths.get(configFilePath);
    }

    /**
     * Read file content as UTF-8 string.
     * @param path file path
     * @return file content
     * @throws IOException read error
     */
    private static String readFileContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Atomically write content to file (write to temp file, then move to target).
     * @param path target file path
     * @param content content to write
     * @throws IOException write error
     */
    private static void writeFileAtomic(Path path, String content) throws IOException {
        Path tempFile = Files.createTempFile(path.getParent(), "lagi_temp_", ".yml");
        try {
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Inject OpenClaw providers information into the "models" section of lagi.yml.
     * @param originalYaml original YAML content
     * @param providersNode providers node
     * @return updated YAML content
     */
    private static String injectModelsIntoYaml(String originalYaml, JsonNode providersNode) {
        String[] lines = originalYaml.split("\\r?\\n", -1);
        int modelsStartLine = findModelsStartLine(lines);

        if (modelsStartLine == -1) {
            return originalYaml;
        }

        int insertPosition = findModelsInsertPosition(lines, modelsStartLine);


        StringBuilder sb = new StringBuilder();
        appendLines(sb, lines, 0, insertPosition);
        appendOpenClawModels(sb, providersNode);
        appendLines(sb, lines, insertPosition, lines.length);

        return sb.toString().trim() + System.lineSeparator(); // 保证文件末尾有换行
    }

    /**
     * Find start line index of "models" node in YAML.
     * @param lines YAML lines
     * @return start line index of models, or -1 if not found
     */
    private static int findModelsStartLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (YAML_MODELS_NODE.equals(lines[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find insert position (line index) after the models section.
     * @param lines YAML lines
     * @param modelsStartLine start line index of models
     * @return insert position index
     */
    private static int findModelsInsertPosition(String[] lines, int modelsStartLine) {
        int insertPos = lines.length;
        for (int i = modelsStartLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            if (!line.startsWith(YAML_INDENT)) {
                insertPos = i;
                break;
            }
        }
        return insertPos;
    }

    /**
     * Append a range of lines into a StringBuilder.
     * @param sb builder
     * @param lines lines array
     * @param start inclusive start index
     * @param end exclusive end index
     */
    private static void appendLines(StringBuilder sb, String[] lines, int start, int end) {
        for (int i = start; i < end; i++) {
            sb.append(lines[i]);
            if (i < end - 1) {
                sb.append(System.lineSeparator());
            }
        }
    }

    /**
     * Build models configuration from OpenClaw providers and append to StringBuilder.
     * @param sb builder
     * @param providersNode providers node
     */
    private static void appendOpenClawModels(StringBuilder sb, JsonNode providersNode) {
        Iterator<Map.Entry<String, JsonNode>> providerIterator = providersNode.fields();
        while (providerIterator.hasNext()) {
            Map.Entry<String, JsonNode> providerEntry = providerIterator.next();
            String providerName = providerEntry.getKey();
            JsonNode providerNode = providerEntry.getValue();

            // Skip empty provider
            if (providerName == null || providerName.trim().isEmpty() || providerNode.isNull()) {
                log.warn("Invalid provider node: name={}", providerName);
                continue;
            }

            // Extract provider base config
            String baseUrl = getNodeTextValue(providerNode, "baseUrl");
            String apiKey = getNodeTextValue(providerNode, "apiKey");
            JsonNode modelsNode = providerNode.get("models");

            // Skip when no models found
            if (modelsNode == null || !modelsNode.isArray() || modelsNode.size() == 0) {
                log.debug("No models found for provider: {}", providerName);
                continue;
            }

            // Iterate each model and generate config
            for (JsonNode modelNode : modelsNode) {
                String modelId = getNodeTextValue(modelNode, "id");
                if (modelId == null) {
                    log.warn("Model id is null for provider: {}", providerName);
                    continue;
                }
                appendModelConfig(sb, providerName, modelId, baseUrl, apiKey);
            }
        }
    }

    /**
     * Append a single model YAML configuration.
     * @param sb builder
     * @param providerName provider name
     * @param modelId model id
     * @param baseUrl API base URL
     * @param apiKey API key
     */
    private static void appendModelConfig(StringBuilder sb, String providerName, String modelId,
                                          String baseUrl, String apiKey) {
        String driverClass = resolveDriverClass(providerName);
        if (driverClass == null) {
            // 这里直接返回，相当于“这一条数据都不会写进 yml 中”
            log.warn("Skip model {} of provider {} because no valid driver class found", modelId, providerName);
            return;
        }

        // Generate normalized model name (lower case, spaces -> '-')
        String modelName = (providerName + "-" + modelId)
                .toLowerCase()
                .replaceAll("\\s+", "-");

        // Write model configuration with strict YAML formatting
        sb.append(System.lineSeparator());
        sb.append(YAML_INDENT).append(YAML_COMMENT_PREFIX).append(" generated from OpenClaw provider ").append(providerName).append(System.lineSeparator());
        sb.append(YAML_INDENT).append("- name: ").append(modelName).append(System.lineSeparator());
        sb.append(YAML_INDENT).append(YAML_INDENT).append("type: ").append(providerName).append(System.lineSeparator());
        sb.append(YAML_INDENT).append(YAML_INDENT).append("enable: true").append(System.lineSeparator());

        // 此时 driverClass 一定非空，且类真实存在
        sb.append(YAML_INDENT).append(YAML_INDENT).append("driver: ").append(driverClass).append(System.lineSeparator());

        sb.append(YAML_INDENT).append(YAML_INDENT).append("model: ").append(modelId).append(System.lineSeparator());

        // Optional fields: only write when non-empty
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            sb.append(YAML_INDENT).append(YAML_INDENT).append("api_address: ").append(baseUrl).append(System.lineSeparator());
        }
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sb.append(YAML_INDENT).append(YAML_INDENT).append("api_key: ").append(apiKey).append(System.lineSeparator());
        }
    }

    /**
     * Safely get text value from JsonNode (handles null and empty).
     * @param node Json node
     * @param fieldName field name
     * @return non-empty text value, or null
     */
    private static String getNodeTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().trim().isEmpty()) {
            return null;
        }
        return fieldNode.asText().trim();
    }

    /**
     * Resolve driver class name from providerName.
     * @param providerName provider name
     * @return full driver class name, or null if cannot resolve
     */
    private static String resolveDriverClass(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            log.warn("Provider name is empty, cannot resolve driver class");
            return null;
        }

        // 拆分provider名称（按非字母数字分隔），取第一个有效片段
        String[] nameParts = providerName.split("[^A-Za-z0-9]+");
        String baseName = null;
        for (String part : nameParts) {
            if (!part.trim().isEmpty()) {
                baseName = part;
                break;
            }
        }

        if (baseName == null) {
            log.warn("No valid part found in provider name: {}", providerName);
            return null;
        }

        StringBuilder className = new StringBuilder();
        className.append(Character.toUpperCase(baseName.charAt(0)));
        if (baseName.length() > 1) {
            className.append(baseName.substring(1).toLowerCase());
        }
        className.append("Adapter");

        String defaultDriver = "ai.llm.adapter.impl." + className;

        String wrapperDriver = null;
        String lowerName = providerName.toLowerCase();
        if (lowerName.contains("alibaba") || lowerName.contains("ali-baba")) {
            wrapperDriver = "ai.wrapper.impl.AlibabaAdapter";
        }

        String resolved = tryLoadClass(wrapperDriver);
        if (resolved != null) {
            return resolved;
        }

        resolved = tryLoadClass(defaultDriver);
        if (resolved != null) {
            return resolved;
        }

        log.warn("No valid driver class found for provider: {}, tried wrapperDriver={} and defaultDriver={}",
                providerName, wrapperDriver, defaultDriver);
        return null;
    }

    private static String tryLoadClass(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            Class.forName(className);
            return className;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}

