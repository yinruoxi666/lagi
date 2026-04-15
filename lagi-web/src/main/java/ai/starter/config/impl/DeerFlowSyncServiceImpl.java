package ai.starter.config.impl;


import ai.starter.InstallerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class DeerFlowSyncServiceImpl extends BaseSyncServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(DeerFlowSyncServiceImpl.class);

    public DeerFlowSyncServiceImpl(String basePath) {
        super(basePath);
    }

    @Override
    public boolean check() {
        Path path = Paths.get(basePath);
        File file = path.toFile();
        if(!file.exists()) {
            log.warn("{}: \t {} is not exists, skipping configuration synchronization", name(), path);
            return false;
        }
        Path configPath = path.resolve("config.yaml");
        if(!configPath.toFile().exists()) {
            log.warn("{}: \t {} is not exists, skipping configuration synchronization", name(), configPath);
            return false;
        }
        return true;
    }

    @Override
    public void export(String path) {
        System.out.println("DeepFlowSyncServiceImpl export");
    }

    @Override
    public void load() {
        Path deerFlowPath = Paths.get(basePath);
        Path configPath = deerFlowPath.resolve("config.yaml");
        if (!Files.exists(configPath)) {
            log.warn("{}: config file is not found: {}", name(), configPath);
            return;
        }
        try {
            YAMLMapper yamlMapper = createYamlMapper();

            Map<String, Object> deerFlowConfig = yamlMapper.readValue(configPath.toFile(), new TypeReference<Map<String,  Object>>() {});
            DeerFlowModelConfig sourceModel = extractFirstModel(deerFlowConfig);
            if (sourceModel == null) {
                log.warn("{}: no valid model found in {}", name(), configPath);
                return;
            }

            Path envPath = deerFlowPath.resolve(".env");
            Map<String, String> envKeyValues = loadEnv(envPath);
            sourceModel.apiKey = resolveEnvPlaceholder(sourceModel.apiKey, envKeyValues);

            Path lagiYmlPath = resolveLagiYmlPath();
            if (!Files.exists(lagiYmlPath)) {
                log.warn("{}: lagi.yml is not found via system property {}", name(), InstallerUtil.CONFIG_FILE_PROPERTY);
                return;
            }

            Map<String, Object> lagiConfig = yamlMapper.readValue(lagiYmlPath.toFile(), new TypeReference<Map<String,  Object>>() {});
            boolean changed = mergeToLagiConfig(lagiConfig, sourceModel);
            if (!changed) {
                log.info("{}: no changes detected for {}", name(), lagiYmlPath);
                return;
            }
            yamlMapper.writeValue(lagiYmlPath.toFile(), lagiConfig);
            log.info("{}: synced model '{}' into {}", name(), sourceModel.backendName, lagiYmlPath);
        } catch (Exception e) {
            log.error("{}: failed to load and sync config", name(), e);
        }
    }

    @Override
    public String name() {
        return "DeerFlow";
    }

    private YAMLMapper createYamlMapper() {
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        return new YAMLMapper(yamlFactory);
    }

    private Path resolveLagiYmlPath() {
        return Paths.get("C:\\Users\\Administrator\\LinkMind\\config\\lagi.yml");
//        String configFilePath = System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY);
//        if (configFilePath == null || configFilePath.trim().isEmpty()) {
//            return null;
//        }
//        return Paths.get(configFilePath.trim());
    }

    @SuppressWarnings("unchecked")
    private DeerFlowModelConfig extractFirstModel(Map<String, Object> deerFlowConfig) {
        if (deerFlowConfig == null) {
            return null;
        }
        Object modelsObj = deerFlowConfig.get("models");
        if (!(modelsObj instanceof List)) {
            return null;
        }
        List<Object> models = (List<Object>) modelsObj;
        for (Object item : models) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> modelEntry = (Map<String, Object>) item;
            String modelId = textOf(modelEntry.get("model"));
            if (isBlank(modelId)) {
                continue;
            }
            String deerModelName = textOf(modelEntry.get("name"));
            String backendName = inferBackendName(deerModelName, modelId);
            if (isBlank(backendName)) {
                continue;
            }
            DeerFlowModelConfig modelConfig = new DeerFlowModelConfig();
            modelConfig.backendName = backendName;
            modelConfig.model = modelId;
            modelConfig.apiBase = firstNonBlank(textOf(modelEntry.get("api_base")), textOf(modelEntry.get("base_url")));
            modelConfig.apiKey = textOf(modelEntry.get("api_key"));
            return modelConfig;
        }
        return null;
    }

    private Map<String, String> loadEnv(Path envPath) throws IOException {
        Map<String, String> envMap = new HashMap<>();
        if (!Files.exists(envPath)) {
            return envMap;
        }
        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            if (!key.isEmpty()) {
                envMap.put(key, value);
            }
        }
        return envMap;
    }

    private String resolveEnvPlaceholder(String value, Map<String, String> envMap) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("$")) {
            return trimmed;
        }
        String key = trimmed.substring(1);
        String resolved = envMap.get(key);
        return isBlank(resolved) ? trimmed : resolved;
    }

    @SuppressWarnings("unchecked")
    private boolean mergeToLagiConfig(Map<String, Object> lagiConfig, DeerFlowModelConfig sourceModel) {
        boolean changed = false;

        List<Map<String, Object>> models = ensureModels(lagiConfig);
        Map<String, Object> targetModel = findModel(models, sourceModel.backendName);
        if (targetModel == null) {
            targetModel = new LinkedHashMap<>();
            targetModel.put("name", sourceModel.backendName);
            targetModel.put("enable", true);
            models.add(targetModel);
            changed = true;
        }

        changed |= upsert(targetModel, "model", sourceModel.model);
        changed |= upsert(targetModel, "enable", true);
        if (!isBlank(sourceModel.apiKey)) {
            changed |= upsert(targetModel, "api_key", sourceModel.apiKey);
        }
        String apiAddress = toCompletionApiAddress(sourceModel.apiBase);
        if (!isBlank(apiAddress)) {
            changed |= upsert(targetModel, "api_address", apiAddress);
        }

        Object functionsObj = lagiConfig.get("functions");
        if (!(functionsObj instanceof Map)) {
            functionsObj = new LinkedHashMap<String, Object>();
            lagiConfig.put("functions", functionsObj);
            changed = true;
        }
        Map<String, Object> functions = (Map<String, Object>) functionsObj;
        Object chatObj = functions.get("chat");
        if (!(chatObj instanceof Map)) {
            chatObj = new LinkedHashMap<String, Object>();
            functions.put("chat", chatObj);
            changed = true;
        }
        Map<String, Object> chat = (Map<String, Object>) chatObj;
        changed |= upsert(chat, "route", "best(" + sourceModel.backendName + ")");

        List<Map<String, Object>> backends = ensureBackends(chat);
        Map<String, Object> backend = findBackend(backends, sourceModel.backendName);
        if (backend == null) {
            backend = new LinkedHashMap<>();
            backend.put("backend", sourceModel.backendName);
            backends.add(backend);
            changed = true;
        }
        changed |= upsert(backend, "model", sourceModel.model);
        changed |= upsert(backend, "enable", true);
        changed |= upsert(backend, "stream", true);
        changed |= upsert(backend, "protocol", "completion");
        changed |= upsert(backend, "priority", 100);
        return changed;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ensureModels(Map<String, Object> lagiConfig) {
        Object modelsObj = lagiConfig.get("models");
        if (modelsObj instanceof List) {
            return (List<Map<String, Object>>) modelsObj;
        }
        List<Map<String, Object>> models = new ArrayList<>();
        lagiConfig.put("models", models);
        return models;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ensureBackends(Map<String, Object> chat) {
        Object backendsObj = chat.get("backends");
        if (backendsObj instanceof List) {
            return (List<Map<String, Object>>) backendsObj;
        }
        List<Map<String, Object>> backends = new ArrayList<>();
        chat.put("backends", backends);
        return backends;
    }

    private Map<String, Object> findModel(List<Map<String, Object>> models, String backendName) {
        for (Map<String, Object> model : models) {
            if (model == null) {
                continue;
            }
            String name = textOf(model.get("name"));
            if (backendName.equalsIgnoreCase(name)) {
                return model;
            }
        }
        return null;
    }

    private Map<String, Object> findBackend(List<Map<String, Object>> backends, String backendName) {
        for (Map<String, Object> backend : backends) {
            if (backend == null) {
                continue;
            }
            String name = textOf(backend.get("backend"));
            if (backendName.equalsIgnoreCase(name)) {
                return backend;
            }
        }
        return null;
    }

    private boolean upsert(Map<String, Object> target, String key, Object value) {
        Object old = target.get(key);
        if (old == null ? value == null : old.equals(value)) {
            return false;
        }
        target.put(key, value);
        return true;
    }

    private String inferBackendName(String deerModelName, String modelId) {
        String source = firstNonBlank(deerModelName, modelId);
        if (isBlank(source)) {
            return null;
        }
        String lower = source.toLowerCase();
        if (lower.contains("qwen")) {
            return "qwen";
        }
        if (lower.contains("deepseek")) {
            return "deepseek";
        }
        if (lower.contains("hunyuan") || lower.contains("tencent")) {
            return "tencent";
        }
        return source;
    }

    private String toCompletionApiAddress(String apiBase) {
        if (isBlank(apiBase)) {
            return null;
        }
        String base = apiBase.trim();
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "chat/completions";
        }
        return base + "/chat/completions";
    }

    private String textOf(Object obj) {
        if (obj == null) {
            return null;
        }
        String value = obj.toString().trim();
        return value.isEmpty() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static class DeerFlowModelConfig {
        private String backendName;
        private String model;
        private String apiBase;
        private String apiKey;
    }

    public static void main(String[] args) {
        DeerFlowSyncServiceImpl service = new DeerFlowSyncServiceImpl("C:\\lz\\work\\lagi");
        service.load();
    }
}
