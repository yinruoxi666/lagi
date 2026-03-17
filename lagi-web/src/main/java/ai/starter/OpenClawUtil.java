package ai.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
public class OpenClawUtil {
    private static final String OPEN_CLAW_DIR_NAME = ".openclaw";
    private static final String[] MODELS_JSON_PATH_SEGMENTS = {"agents", "main", "agent", "models.json"};
    private static final String CONFIG_FILE_PROPERTY = Application.CONFIG_FILE_PROPERTY;
    private static final String YAML_INDENT = "  ";
    private static final String[] RESPONSE_SUPPORTED_BACKENDS = {"qwen", "openai"};
    private static final Set<String> RESPONSE_BACKENDS_SET = new HashSet<>(Arrays.asList(RESPONSE_SUPPORTED_BACKENDS));

    private static final Yaml SNAKE_YAML = createSnakeYaml();


    public static void sync(int port) {
        syncToOpenClaw(port);
        syncToLinkMind();
    }

    public static void syncToOpenClaw(int port) {
        OpenClawInjector.inject("http://127.0.0.1:" + port + "/v1");
    }

    public static void syncToLinkMind() {
        try {
            Path lagiYmlPath = getLagiYmlPath();
            if (lagiYmlPath == null || !Files.exists(lagiYmlPath)) {
                return;
            }

            Map<String, Object> root = loadYamlAsMap(lagiYmlPath);

            // Resolve primary from openclaw.json: agents.defaults.model.primary -> (backendName, modelId)
            PrimaryBackend primaryBackend = resolvePrimaryBackend(lagiYmlPath.getParent(), root);

            // Read models.json and merge model list / primary backends
            JsonNode providersNode = readAndParseModelsJson();
            if (providersNode != null) {
                OpenClawFragmentsResult fragmentsResult = generateOpenClawFragments(providersNode, primaryBackend);
                if (fragmentsResult != null && !fragmentsResult.fragments.isEmpty()) {
                    boolean changed = mergeOpenClawIntoYaml(root, fragmentsResult.selectedModels, primaryBackend);
                    if (changed) {
                        writeYaml(lagiYmlPath, root);
                        log.info("Successfully synced OpenClaw configuration to {}", lagiYmlPath);
                        return;
                    }
                }
            }

            // If no models.json sync, still apply primary backend only (backends = single primary)
            if (primaryBackend != null) {
                boolean changed = mergeOpenClawIntoYaml(root, null, primaryBackend);
                if (changed) {
                    writeYaml(lagiYmlPath, root);
                    log.info("Successfully synced primary backend from openclaw.json to {}", lagiYmlPath);
                }
            } else {
                log.info("No changes needed, lagi.yml already up to date");
            }

        } catch (Exception e) {
            log.error("Failed to sync OpenClaw config", e);
        }
    }


    private static Path getLagiYmlPath() {
        String configFilePath = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFilePath == null || configFilePath.trim().isEmpty()) {
            return null;
        }
        return Paths.get(configFilePath);
    }


    private static final String OPENCLAW_JSON = "openclaw.json";

    /**
     * Reads openclaw.json from configDir and returns agents.defaults.model.primary (e.g. "zai/glm-4.7-flash").
     */
    private static String readOpenclawPrimary(Path configDir) throws IOException {
        if (configDir == null || !Files.isDirectory(configDir)) {
            return null;
        }
        Path openclawPath = configDir.resolve(OPENCLAW_JSON);
        if (!Files.exists(openclawPath)) {
            return null;
        }
        String content = readFileContent(openclawPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode agents = root != null ? root.get("agents") : null;
        JsonNode defaults = agents != null && agents.isObject() ? agents.get("defaults") : null;
        JsonNode model = defaults != null && defaults.isObject() ? defaults.get("model") : null;
        JsonNode primary = model != null && model.isObject() ? model.get("primary") : null;
        if (primary == null || primary.isNull() || !primary.isTextual()) {
            return null;
        }
        String value = primary.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static final String OPENAI_COMPLETIONS_API = "openai-completions";

    /**
     * Reads openclaw.json from configDir and returns models.providers.{provider}.api for the given provider.
     */
    private static String readOpenclawProviderApi(Path configDir, String provider) throws IOException {
        if (configDir == null || !Files.isDirectory(configDir) || provider == null || provider.trim().isEmpty()) {
            return null;
        }
        Path openclawPath = configDir.resolve(OPENCLAW_JSON);
        if (!Files.exists(openclawPath)) {
            return null;
        }
        String content = readFileContent(openclawPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode models = root != null ? root.get("models") : null;
        JsonNode providers = models != null && models.isObject() ? models.get("providers") : null;
        JsonNode providerNode = providers != null && providers.isObject() ? providers.get(provider) : null;
        JsonNode apiNode = providerNode != null && providerNode.isObject() ? providerNode.get("api") : null;
        if (apiNode == null || apiNode.isNull() || !apiNode.isTextual()) {
            return null;
        }
        String value = apiNode.asText().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Resolves openclaw primary "provider/modelId" to (backendName, modelId) if that backend exists in lagi.yml models.
     * If no match but the primary's provider has api "openai-completions", returns backend "custom" with the modelId.
     */
    @SuppressWarnings("unchecked")
    private static PrimaryBackend resolvePrimaryBackend(Path configDir, Map<String, Object> root) {
        String primary = null;
        try {
            primary = readOpenclawPrimary(configDir);
        } catch (IOException e) {
            log.debug("Could not read openclaw.json: {}", e.getMessage());
            return null;
        }
        if (primary == null || primary.trim().isEmpty()) {
            return null;
        }
        int slash = primary.indexOf('/');
        if (slash <= 0 || slash == primary.length() - 1) {
            log.debug("Invalid openclaw primary format: {}", primary);
            return null;
        }
        String provider = primary.substring(0, slash).trim();
        String modelId = primary.substring(slash + 1).trim();
        String backendName = provider;
        List<Map<String, Object>> modelsList = (List<Map<String, Object>>) root.get("models");
        if (modelsList == null || modelsList.isEmpty()) {
            return null;
        }
        for (Object item : modelsList) {
            if (!(item instanceof Map)) continue;
            Object nameObj = ((Map<?, ?>) item).get("name");
            Object type = ((Map<?, ?>) item).get("type");
            if (nameObj == null) continue;
            if (backendName.equalsIgnoreCase(nameObj.toString().trim())) {
                return new PrimaryBackend(nameObj.toString(), type.toString(), modelId);
            }
        }
        // No match in lagi.yml: if primary provider uses openai-completions API, return "custom" backend
        try {
            String providerApi = readOpenclawProviderApi(configDir, provider);
            if (OPENAI_COMPLETIONS_API.equalsIgnoreCase(providerApi != null ? providerApi.trim() : null)) {
                return new PrimaryBackend("custom", "OpenAICompatible", modelId);
            }
        } catch (IOException e) {
            log.debug("Could not read provider api from openclaw.json: {}", e.getMessage());
        }
        log.debug("Primary backend {} not found in lagi.yml models", backendName);
        return null;
    }

    /**
     * Reads and parses models.json. Tries (1) same directory as lagi.yml, then (2) OpenClaw path.
     *
     * @return providers node, or null if not found / invalid
     */
    private static JsonNode readAndParseModelsJson() throws IOException {
        Path modelsJsonPath = null;
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            Path openClawDir = Paths.get(userHome, OPEN_CLAW_DIR_NAME);
            if (Files.isDirectory(openClawDir)) {
                Path openClawModels = openClawDir;
                for (String segment : MODELS_JSON_PATH_SEGMENTS) {
                    openClawModels = openClawModels.resolve(segment);
                }
                if (Files.exists(openClawModels)) {
                    modelsJsonPath = openClawModels;
                    log.debug("Using OpenClaw models.json: {}", modelsJsonPath);
                }
            }
        }
        if (modelsJsonPath == null || !Files.exists(modelsJsonPath)) {
            log.warn("models.json not found (tried next to lagi.yml and OpenClaw path)");
            return null;
        }

        String content = readFileContent(modelsJsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode providersNode = root.get("providers");

        return (providersNode != null && providersNode.isObject()) ? providersNode : null;
    }


    private static String readFileContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // 写临时文件后 move 覆盖目标文件
    private static void writeFileAtomic(Path path, String content) throws IOException {
        Path tempFile = Files.createTempFile(path.getParent(), "lagi_openclaw_temp_", ".yml");
        try {
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static Yaml createSnakeYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    private static Map<String, Object> loadYamlAsMap(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = SNAKE_YAML.load(in);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("content", loaded);
            return root;
        }
    }

    private static void writeYaml(Path path, Map<String, Object> root) throws IOException {
        String yaml = SNAKE_YAML.dump(root);
        writeFileAtomic(path, yaml + System.lineSeparator());
    }

    /**
     * Merges OpenClaw selected models into the lagi.yml root map: adds/updates models list.
     * If primaryBackend is set, functions.chat.backends is replaced with a single element (primary only);
     * otherwise backends are not modified.
     */
    @SuppressWarnings("unchecked")
    private static boolean mergeOpenClawIntoYaml(Map<String, Object> root,
                                                 List<Map<String, Object>> selectedModels,
                                                 PrimaryBackend primaryBackend) {
        boolean changed = false;
        if (selectedModels == null) {
            selectedModels = Collections.emptyList();
        }

        // Merge into top-level "models" list (by name) only when we have selected models from models.json
        if (!selectedModels.isEmpty()) {
            List<Map<String, Object>> modelsList = (List<Map<String, Object>>) root.get("models");
            if (modelsList == null) {
                modelsList = new ArrayList<>();
                root.put("models", modelsList);
                changed = true;
            }
            Set<String> existingModelNames = new HashSet<>();
            for (Object item : modelsList) {
                if (item instanceof Map) {
                    Object n = ((Map<?, ?>) item).get("name");
                    if (n != null) {
                        existingModelNames.add(n.toString().trim());
                    }
                }
            }
            Map<String, Map<String, Object>> selectedByName = new HashMap<>();
            for (Map<String, Object> m : selectedModels) {
                String name = m.get("name") == null ? null : m.get("name").toString().trim();
                if (name != null && !name.isEmpty()) {
                    selectedByName.put(name, m);
                }
            }
            for (Object item : modelsList) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> entry = (Map<String, Object>) item;
                Object nameObj = entry.get("name");
                if (nameObj == null) continue;
                String name = nameObj.toString().trim();
                Map<String, Object> selected = selectedByName.get(name);
                if (selected == null) continue;
                if (selected.get("model") != null && !selected.get("model").toString().trim().isEmpty()) {
                    entry.put("model", selected.get("model"));
                    changed = true;
                }
                if (selected.get("api_address") != null && !selected.get("api_address").toString().trim().isEmpty()) {
                    entry.put("api_address", selected.get("api_address") + "/chat/completions");
                    changed = true;
                }
                if (selected.get("api_key") != null && !selected.get("api_key").toString().trim().isEmpty()) {
                    entry.put("api_key", selected.get("api_key"));
                    changed = true;
                }
                if (selected.get("enable") != null) {
                    entry.put("enable", selected.get("enable"));
                    changed = true;
                }
            }
            for (Map<String, Object> m : selectedModels) {
                String name = m.get("name") == null ? null : m.get("name").toString().trim();
                if (name == null || name.isEmpty()) continue;
                if (existingModelNames.contains(name)) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                if (m.get("type") != null && !m.get("type").toString().trim().isEmpty()) {
                    entry.put("type", m.get("type"));
                }
                if (m.get("model") != null && !m.get("model").toString().trim().isEmpty()) {
                    entry.put("model", m.get("model"));
                }
                if (m.get("driver") != null && !m.get("driver").toString().trim().isEmpty()) {
                    entry.put("driver", m.get("driver"));
                }
                entry.put("enable", m.getOrDefault("enable", Boolean.TRUE));
                if (m.get("api_address") != null && !m.get("api_address").toString().trim().isEmpty()) {
                    entry.put("api_address", m.get("api_address"));
                }
                if (m.get("api_key") != null && !m.get("api_key").toString().trim().isEmpty()) {
                    entry.put("api_key", m.get("api_key"));
                }
                modelsList.add(entry);
                existingModelNames.add(name);
                changed = true;
            }
        }

        // If primary from openclaw.json matched: replace functions.chat.backends with single primary backend only
        if (primaryBackend != null) {
            Map<String, Object> functions = (Map<String, Object>) root.get("functions");
            if (functions == null) {
                functions = new LinkedHashMap<>();
                root.put("functions", functions);
                changed = true;
            }
            Map<String, Object> chat = (Map<String, Object>) functions.get("chat");
            if (chat == null) {
                chat = new LinkedHashMap<>();
                functions.put("chat", chat);
                changed = true;
            }
            List<Map<String, Object>> backendsList = new ArrayList<>();
            Map<String, Object> singleBackend = new LinkedHashMap<>();
            singleBackend.put("backend", primaryBackend.getName());
            singleBackend.put("model", primaryBackend.getModel());
            singleBackend.put("enable", true);
            singleBackend.put("stream", true);
            singleBackend.put("protocol", "completion");
            singleBackend.put("priority", 0);
            backendsList.add(singleBackend);
            chat.put("backends", backendsList);
            chat.put("route", "best(" + primaryBackend.getName() + ")");
            changed = true;
        }
        return changed;
    }

    // Match models.json providers against models defined in lagi.yml
    @SuppressWarnings("unchecked")
    private static OpenClawFragmentsResult generateOpenClawFragments(JsonNode providersNode, PrimaryBackend primaryBackend) throws IOException {
        Path lagiYmlPath = getLagiYmlPath();
        if (lagiYmlPath == null) {
            throw new IOException("Cannot resolve lagi.yml path");
        }
        if (!Files.exists(lagiYmlPath)) {
            throw new IOException("lagi.yml not found at: " + lagiYmlPath);
        }

        YAMLMapper yamlMapper = createYamlMapper();
        Map<String, Object> modelRoot = yamlMapper.readValue(Files.newInputStream(lagiYmlPath), Map.class);
        List<Map<String, Object>> allModelDefs = (List<Map<String, Object>>) modelRoot.get("models");
        if (allModelDefs == null || allModelDefs.isEmpty()) {
            throw new IOException("No models defined in lagi.yml");
        }

        // Match models.json providers against model entries in lagi.yml
        List<Map<String, Object>> selectedModels = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> providerIterator = providersNode.fields();
        while (providerIterator.hasNext()) {
            Map.Entry<String, JsonNode> providerEntry = providerIterator.next();
            String providerName = providerEntry.getKey();
            JsonNode providerNode = providerEntry.getValue();
            if (providerName == null || providerName.trim().isEmpty() || providerNode == null || providerNode.isNull()) {
                continue;
            }

            JsonNode modelsNode = providerNode.get("models");
            if (modelsNode == null || !modelsNode.isArray() || modelsNode.size() == 0) {
                continue;
            }

            // 使用该 provider 下的第一个 model 进行匹配
            JsonNode firstModelNode = modelsNode.get(0);
            if (firstModelNode == null || firstModelNode.isNull()) {
                continue;
            }

            Map<String, Object> matched = findMatchingModelDef(allModelDefs, providerName, firstModelNode, primaryBackend);
            if (matched == null) {
                log.warn("No matching model found in lagi.yml for provider: {}, skip", providerName);
                continue;
            }

            // 避免同一个 name 重复写入
            String name = matched.get("name") == null ? null : matched.get("name").toString();
            if (name != null && usedNames.contains(name)) {
                continue;
            }
            // Build comma-separated model ids from this provider in models.json
            List<String> modelIds = new ArrayList<>();
            for (JsonNode mNode : modelsNode) {
                String id = getNodeTextValue(mNode, "id");
                if (id != null && !id.isEmpty()) {
                    modelIds.add(id);
                }
            }
            String modelValue = modelIds.isEmpty() ? null : String.join(",", modelIds);

            Map<String, Object> copy = new LinkedHashMap<>();

            if (matched.get("name") != null) {
                copy.put("name", matched.get("name"));
            }
            if (matched.get("type") != null) {
                copy.put("type", matched.get("type"));
            }

            if (modelValue != null) {
                copy.put("model", modelValue);
            } else if (matched.get("model") != null) {
                copy.put("model", matched.get("model"));
            }
            // 保留 driver 字段，如果有的话
            if (matched.get("driver") != null) {
                copy.put("driver", matched.get("driver"));
            }

            copy.put("enable", Boolean.TRUE);

            String baseUrl = getNodeTextValue(providerNode, "baseUrl");
            String apiKey = getNodeTextValue(providerNode, "apiKey");
            if (baseUrl != null && !baseUrl.isEmpty()) {
                copy.put("api_address", baseUrl);
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                copy.put("api_key", apiKey);
            }

            selectedModels.add(copy);
            if (name != null) {
                usedNames.add(name);
            }
        }

        if (selectedModels.isEmpty()) {
            log.warn("No matching models found in lagi.yml for providers.json, skip sync");
            return null;
        }

        // models fragment: only append the primary model's content; backends fragment: all selected
        StringBuilder modelsFragment = new StringBuilder();
        StringBuilder backendsFragment = new StringBuilder();

        boolean isFirstBackend = true;
        for (Map<String, Object> m : selectedModels) {
            String name = m.get("name") == null ? "" : m.get("name").toString();
            String type = m.get("type") == null ? "" : m.get("type").toString();
            String modelField = m.get("model") == null ? "" : m.get("model").toString();
            String driver = m.get("driver") == null ? "" : m.get("driver").toString();
            String apiAddress = m.get("api_address") == null ? "" : m.get("api_address").toString();
            String apiKey = m.get("api_key") == null ? "" : m.get("api_key").toString();

            // Append to models fragment only when this is the primary model
            boolean isPrimary = primaryBackend != null && primaryBackend.getType().equalsIgnoreCase(type);
            if (isPrimary) {
                String primaryModelField = primaryBackend.getModel() != null ? primaryBackend.getModel() : modelField;
                modelsFragment.append(YAML_INDENT).append("- name: ").append(name).append(System.lineSeparator());
                if (!type.isEmpty()) {
                    modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("type: ").append(type).append(System.lineSeparator());
                }
                if (!primaryModelField.isEmpty()) {
                    modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("model: ").append(primaryModelField).append(System.lineSeparator());
                }
                if (!driver.isEmpty()) {
                    modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("driver: ").append(driver).append(System.lineSeparator());
                }
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("enable: true").append(System.lineSeparator());
                if (!apiAddress.isEmpty()) {
                    modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("api_address: ").append(apiAddress).append(System.lineSeparator());
                }
                if (!apiKey.isEmpty()) {
                    modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("api_key: ").append(apiKey).append(System.lineSeparator());
                }
            }

            // Append to functions.chat.backends fragment (all selected)
            if (!isFirstBackend) {
                backendsFragment.append(System.lineSeparator());
            }
            backendsFragment.append(repeatIndent(3))
                    .append("- backend: ").append(name).append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("model: ").append(modelField).append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("enable: true").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("stream: true").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("protocol: completion").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("priority: 100").append(System.lineSeparator());

            isFirstBackend = false;
        }

        return new OpenClawFragmentsResult(
                new OpenClawFragments(modelsFragment.toString(), backendsFragment.toString()),
                selectedModels
        );
    }

    private static String repeatIndent(int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(YAML_INDENT);
        }
        return sb.toString();
    }

    private static YAMLMapper createYamlMapper() {
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory factory =
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                        .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        return new YAMLMapper(factory);
    }

    /**
     * Finds the model def in lagi.yml that matches the provider (and optionally the primary backend).
     * When primaryBackend is not null, only returns the def that corresponds to the primary (def name equals primaryBackend.backendName).
     */
    private static Map<String, Object> findMatchingModelDef(List<Map<String, Object>> allModelDefs,
                                                            String providerName,
                                                            JsonNode modelNode,
                                                            PrimaryBackend primaryBackend) {
//        String providerLower = providerName == null ? "" : providerName.toLowerCase();
        String primaryBackendName = (primaryBackend != null && primaryBackend.getType() != null)
                ? primaryBackend.getType().trim().toLowerCase() : null;

        for (Map<String, Object> def : allModelDefs) {
            if (def == null) continue;
            String type = def.get("type") == null ? "" : def.get("type").toString();
            String typeLower = type.toLowerCase();
            if (primaryBackendName != null && !primaryBackendName.equals(typeLower)) {
                continue;
            }
            return def;
        }
        return null;
    }

    // 从 JsonNode 安全取字符串字段
    private static String getNodeTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().trim().isEmpty()) {
            return null;
        }
        return fieldNode.asText().trim();
    }

    // 追加到 models / backends
    private static class OpenClawFragments {
        final String modelsFragment;    // 要追加到 models: 下的内容
        final String backendsFragment;  // 要追加到 functions.chat.backends 下的内容

        OpenClawFragments(String modelsFragment, String backendsFragment) {
            this.modelsFragment = modelsFragment;
            this.backendsFragment = backendsFragment;
        }

        boolean isEmpty() {
            return (modelsFragment == null || modelsFragment.trim().isEmpty()) &&
                    (backendsFragment == null || backendsFragment.trim().isEmpty());
        }
    }

    private static class OpenClawFragmentsResult {
        final OpenClawFragments fragments;
        final List<Map<String, Object>> selectedModels;

        OpenClawFragmentsResult(OpenClawFragments fragments, List<Map<String, Object>> selectedModels) {
            this.fragments = fragments;
            this.selectedModels = selectedModels;
        }
    }


    @Data
    @AllArgsConstructor
    private static class PrimaryBackend {
        private String name;
        private String type;
        private String model;
    }
}

