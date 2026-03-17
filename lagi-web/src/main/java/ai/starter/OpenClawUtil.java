package ai.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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
    private static final String YAML_COMMENT_PREFIX = "#";

    private static final Yaml SNAKE_YAML = createSnakeYaml();


    public static void sync(int port) {
//        syncToOpenClaw(port);
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

            // 读取并解析 OpenClaw 的 models.json
            JsonNode providersNode = readAndParseModelsJson();
            if (providersNode == null) {
                return;
            }

            OpenClawFragmentsResult fragmentsResult = generateOpenClawFragments(providersNode);
            if (fragmentsResult == null || fragmentsResult.fragments.isEmpty()) {
                log.warn("No OpenClaw models to sync, skip");
                return;
            }

            // 4) Write to lagi.yml using SnakeYAML
            Map<String, Object> root = loadYamlAsMap(lagiYmlPath);
            boolean changed = mergeOpenClawIntoYaml(root, fragmentsResult.selectedModels);
            if (!changed) {
                log.info("No changes needed, lagi.yml already contains OpenClaw configuration");
                return;
            }
            writeYaml(lagiYmlPath, root);
            log.info("Successfully synced OpenClaw configuration to {}", lagiYmlPath);

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


    private static JsonNode readAndParseModelsJson() throws IOException {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            log.warn("Cannot get user home directory");
            return null;
        }

        Path openClawDir = Paths.get(userHome, OPEN_CLAW_DIR_NAME);
        if (!Files.isDirectory(openClawDir)) {
            log.warn("OpenClaw directory does not exist: {}", openClawDir);
            return null;
        }

        Path modelsJsonPath = openClawDir;
        for (String segment : MODELS_JSON_PATH_SEGMENTS) {
            modelsJsonPath = modelsJsonPath.resolve(segment);
        }

        if (!Files.exists(modelsJsonPath)) {
            log.warn("models.json does not exist: {}", modelsJsonPath);
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
     * Merges OpenClaw selected models into the lagi.yml root map: adds/updates models list,
     * functions.chat.backends, and functions.chat.route. Returns true if any change was made.
     */
    @SuppressWarnings("unchecked")
    private static boolean mergeOpenClawIntoYaml(Map<String, Object> root,
                                                 List<Map<String, Object>> selectedModels) {
        if (selectedModels == null || selectedModels.isEmpty()) {
            return false;
        }
        boolean changed = false;

        // Merge into top-level "models" list (by name)
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
        for (Map<String, Object> m : selectedModels) {
            String name = m.get("name") == null ? null : m.get("name").toString().trim();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (existingModelNames.contains(name)) {
                continue;
            }
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

        // Ensure functions.chat.backends and merge backends
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
        List<Map<String, Object>> backendsList = (List<Map<String, Object>>) chat.get("backends");
        if (backendsList == null) {
            backendsList = new ArrayList<>();
            chat.put("backends", backendsList);
            changed = true;
        }
        Set<String> existingBackendNames = new HashSet<>();
        for (Object item : backendsList) {
            if (item instanceof Map) {
                Object b = ((Map<?, ?>) item).get("backend");
                if (b != null) {
                    existingBackendNames.add(b.toString().trim());
                }
            }
        }
        for (Map<String, Object> m : selectedModels) {
            String name = m.get("name") == null ? null : m.get("name").toString().trim();
            String modelField = m.get("model") == null ? "" : m.get("model").toString().trim();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (existingBackendNames.contains(name)) {
                continue;
            }
            Map<String, Object> backendEntry = new LinkedHashMap<>();
            backendEntry.put("backend", name);
            backendEntry.put("model", modelField.isEmpty() ? name : modelField);
            backendEntry.put("enable", true);
            backendEntry.put("stream", true);
            backendEntry.put("protocol", "completion");
            backendEntry.put("priority", 100);
            backendsList.add(backendEntry);
            existingBackendNames.add(name);
            changed = true;
        }

        // Update functions.chat.route with best((a|b|c))
        List<String> backendNames = new ArrayList<>();
        for (Map<String, Object> m : selectedModels) {
            Object nameObj = m == null ? null : m.get("name");
            if (nameObj == null) continue;
            String n = nameObj.toString().trim();
            if (!n.isEmpty() && !backendNames.contains(n)) {
                backendNames.add(n);
            }
        }
        if (!backendNames.isEmpty()) {
            String existingRoute = null;
            Object routeObj = chat.get("route");
            if (routeObj != null) {
                existingRoute = routeObj.toString().trim();
            }
            String newRoute = updateBestRoute(existingRoute != null ? existingRoute : "", backendNames);
            if (!newRoute.equals(existingRoute != null ? existingRoute : "")) {
                chat.put("route", newRoute);
                changed = true;
            }
        }

        return changed;
    }

    // models.json 的 providers 与 model.yml 做匹配
    @SuppressWarnings("unchecked")
    private static OpenClawFragmentsResult generateOpenClawFragments(JsonNode providersNode) throws IOException {

        Path lagiYmlPath = getLagiYmlPath();
        if (lagiYmlPath == null) {
            throw new IOException("Cannot resolve lagi.yml path for locating model.yml");
        }
        Path modelYmlPath = lagiYmlPath.getParent().resolve("model.yml");
        if (!Files.exists(modelYmlPath)) {
            throw new IOException("model.yml not found at: " + modelYmlPath);
        }

        YAMLMapper yamlMapper = createYamlMapper();
        Map<String, Object> modelRoot = yamlMapper.readValue(Files.newInputStream(modelYmlPath), Map.class);
        List<Map<String, Object>> allModelDefs = (List<Map<String, Object>>) modelRoot.get("models");
        if (allModelDefs == null || allModelDefs.isEmpty()) {
            throw new IOException("No models defined in model.yml");
        }

        // 根据 models.json 的 providers 去匹配 model.yml 中的模型块
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

            Map<String, Object> matched = findMatchingModelDef(allModelDefs, providerName, firstModelNode);
            if (matched == null) {
                log.warn("No matching model found in model.yml for provider: {}, skip", providerName);
                continue;
            }

            // 避免同一个 name 重复写入
            String name = matched.get("name") == null ? null : matched.get("name").toString();
            if (name != null && usedNames.contains(name)) {
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>();

            if (matched.get("name") != null) {
                copy.put("name", matched.get("name"));
            }
            if (matched.get("type") != null) {
                copy.put("type", matched.get("type"));
            }

            if (matched.get("model") != null) {
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
            log.warn("No matching models found in model.yml for providers.json, skip sync");
            return null;
        }


        // models 和 backends
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

            // 追加到 models下的内容
            modelsFragment.append(YAML_INDENT).append("- name: ").append(name).append(System.lineSeparator());
            if (!type.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("type: ").append(type).append(System.lineSeparator());
            }
            if (!modelField.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("model: ").append(modelField).append(System.lineSeparator());
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

            // 追加到 functions.chat.backends 下的内容

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

    // Merge backend names into best( (a|b|c) ) route expression
    private static String updateBestRoute(String routeExpr, List<String> backendNames) {
        if (backendNames == null || backendNames.isEmpty()) {
            return routeExpr == null ? "" : routeExpr;
        }
        String route = routeExpr == null ? "" : routeExpr.trim();

        // 没有 route 或不是 best(...)，给一个最简单的 best( (a|b|c) )
        if (route.isEmpty() || !route.startsWith("best(") || !route.endsWith(")")) {
            return "best((" + String.join("|", backendNames) + "))";
        }

        String inside = route.substring("best(".length(), route.length() - 1);
        int depth = 0;
        int splitAt = -1;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                splitAt = i;
                break;
            }
        }

        if (splitAt < 0) {
            String updated = addNamesToGroup(inside.trim(), backendNames);
            return "best(" + updated + ")";
        }

        String arg1 = inside.substring(0, splitAt).trim();
        String arg2 = inside.substring(splitAt + 1).trim();
        String updatedArg2 = addNamesToGroup(arg2, backendNames);
        if (updatedArg2.equals(arg2)) {
            return route;
        }
        return "best(" + arg1 + "," + updatedArg2 + ")";
    }

    // 在分组里追加缺失 backend 名称
    private static String addNamesToGroup(String groupExpr, List<String> backendNames) {
        String g = groupExpr == null ? "" : groupExpr.trim();
        boolean wrapped = g.startsWith("(") && g.endsWith(")");
        String inner = wrapped ? g.substring(1, g.length() - 1).trim() : g;

        List<String> tokens = new ArrayList<>();
        if (!inner.isEmpty()) {
            // 只按 '|' 拆
            for (String t : inner.split("\\|")) {
                String tt = t.trim();
                if (!tt.isEmpty() && !tokens.contains(tt)) {
                    tokens.add(tt);
                }
            }
        }

        boolean changed = false;
        for (String name : backendNames) {
            if (name == null || name.trim().isEmpty()) continue;
            if (!tokens.contains(name)) {
                tokens.add(name);
                changed = true;
            }
        }

        if (!changed) {
            return groupExpr == null ? "" : groupExpr.trim();
        }

        String rebuilt = String.join("|", tokens);
        return wrapped ? "(" + rebuilt + ")" : rebuilt;
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

    private static Map<String, Object> findMatchingModelDef(List<Map<String, Object>> allModelDefs,
                                                            String providerName,
                                                            JsonNode modelNode) {
        String providerLower = providerName == null ? "" : providerName.toLowerCase();
        String modelId = getNodeTextValue(modelNode, "id");
        String modelName = getNodeTextValue(modelNode, "name");
        String modelIdLower = modelId == null ? "" : modelId.toLowerCase();
        String modelNameLower = modelName == null ? "" : modelName.toLowerCase();

        //  provider 映射
        String aliasName = null;
        if ("moonshot".equalsIgnoreCase(providerName)) {
            aliasName = "kimi";
        } else if ("zai".equalsIgnoreCase(providerName)) {
            aliasName = "chatglm";
        } else if ("qwen-portal".equalsIgnoreCase(providerName)) {
            aliasName = "qwen";
        }
        String aliasLower = aliasName == null ? "" : aliasName.toLowerCase();

        for (Map<String, Object> def : allModelDefs) {
            if (def == null) continue;
            String name = def.get("name") == null ? "" : def.get("name").toString();
            String type = def.get("type") == null ? "" : def.get("type").toString();
            Object modelFieldObj = def.get("model");
            String modelField = modelFieldObj == null ? "" : modelFieldObj.toString();

            String nameLower = name.toLowerCase();
            String typeLower = type.toLowerCase();
            String modelFieldLower = modelField.toLowerCase();

            if (!nameLower.isEmpty() && providerLower.contains(nameLower)) {
                return def;
            }
            if (!aliasLower.isEmpty() && nameLower.equals(aliasLower)) {
                return def;
            }

            // provider 与 type 近似
            if (!typeLower.isEmpty() &&
                    (providerLower.contains(typeLower) || typeLower.contains(providerLower))) {
                return def;
            }

            // models.json 的 id/name 在 model.yml 的 model 字段中出现
            if (!modelIdLower.isEmpty() && modelFieldLower.contains(modelIdLower)) {
                return def;
            }
            if (!modelNameLower.isEmpty() && modelFieldLower.contains(modelNameLower)) {
                return def;
            }
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
}

