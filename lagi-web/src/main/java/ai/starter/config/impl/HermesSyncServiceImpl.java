package ai.starter.config.impl;

import ai.common.pojo.Backend;
import ai.config.GlobalConfigurations;
import ai.starter.config.util.ConfigUtil;
import ai.utils.YmlLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class HermesSyncServiceImpl extends BaseSyncServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(HermesSyncServiceImpl.class);

    private static final String CONFIG_YAML = "config.yaml";
    private static final String ENV_FILE = ".env";

    private static final Map<String, String> PROVIDER_API_KEY_MAP = new HashMap<>();
    private static final Map<String, String> PROVIDER_DRIVER_MAP = new HashMap<>();
    private static final Map<String, String> PROVIDER_BASE_URL_KEY_MAP = new HashMap<>();

    static {
        PROVIDER_API_KEY_MAP.put("alibaba", "DASHSCOPE_API_KEY");
        PROVIDER_API_KEY_MAP.put("openrouter", "OPENROUTER_API_KEY");
        PROVIDER_API_KEY_MAP.put("google", "GOOGLE_API_KEY");
        PROVIDER_API_KEY_MAP.put("gemini", "GEMINI_API_KEY");
        PROVIDER_API_KEY_MAP.put("zai", "GLM_API_KEY");
        PROVIDER_API_KEY_MAP.put("glm", "GLM_API_KEY");
        PROVIDER_API_KEY_MAP.put("kimi", "KIMI_API_KEY");
        PROVIDER_API_KEY_MAP.put("moonshot", "KIMI_API_KEY");
        PROVIDER_API_KEY_MAP.put("arceeai", "ARCEEAI_API_KEY");
        PROVIDER_API_KEY_MAP.put("minimax", "MINIMAX_API_KEY");
        PROVIDER_API_KEY_MAP.put("huggingface", "HF_TOKEN");
        PROVIDER_API_KEY_MAP.put("xiaomi", "XIAOMI_API_KEY");

        PROVIDER_BASE_URL_KEY_MAP.put("alibaba", "DASHSCOPE_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("google", "GEMINI_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("gemini", "GEMINI_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("zai", "GLM_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("glm", "GLM_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("kimi", "KIMI_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("moonshot", "KIMI_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("arceeai", "ARCEE_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("minimax", "MINIMAX_BASE_URL");
        PROVIDER_BASE_URL_KEY_MAP.put("xiaomi", "XIAOMI_BASE_URL");

        PROVIDER_DRIVER_MAP.put("alibaba", "ai.llm.adapter.impl.OpenAIStandardAdapter");
        PROVIDER_DRIVER_MAP.put("openrouter", "ai.llm.adapter.impl.OpenAIStandardAdapter");
        PROVIDER_DRIVER_MAP.put("google", "ai.llm.adapter.impl.GeminiAdapter");
        PROVIDER_DRIVER_MAP.put("gemini", "ai.llm.adapter.impl.GeminiAdapter");
        PROVIDER_DRIVER_MAP.put("zai", "ai.llm.adapter.impl.ZhipuAdapter");
        PROVIDER_DRIVER_MAP.put("glm", "ai.llm.adapter.impl.ZhipuAdapter");
        PROVIDER_DRIVER_MAP.put("kimi", "ai.llm.adapter.impl.MoonshotAdapter");
        PROVIDER_DRIVER_MAP.put("moonshot", "ai.llm.adapter.impl.MoonshotAdapter");
        PROVIDER_DRIVER_MAP.put("arceeai", "ai.llm.adapter.impl.OpenAIStandardAdapter");
        PROVIDER_DRIVER_MAP.put("minimax", "ai.llm.adapter.impl.OpenAIStandardAdapter");
        PROVIDER_DRIVER_MAP.put("huggingface", "ai.llm.adapter.impl.OpenAIStandardAdapter");
        PROVIDER_DRIVER_MAP.put("xiaomi", "ai.llm.adapter.impl.OpenAIStandardAdapter");
    }

    public HermesSyncServiceImpl(String basePath) {
        super(basePath);
        if (isBlank(this.basePath)) {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                this.basePath = Paths.get(userHome, ".hermes").toString();
            }
        }
    }

    @Override
    public boolean check() {
        Path path = Paths.get(basePath);
        if (!path.toFile().exists()) {
            return false;
        }
        if (!path.toFile().isDirectory()) {
            return false;
        }
        Path configPath = path.resolve(CONFIG_YAML);
        return configPath.toFile().exists();
    }

    @Override
    public void export(String path) {
        Path hermesPath = Paths.get(basePath);
        if (!hermesPath.toFile().exists() || !hermesPath.toFile().isDirectory()) {
            log.warn("{}: {} does not exist or is not a directory, skipping configuration synchronization", name(), hermesPath);
            return;
        }
        Path configPath = hermesPath.resolve(CONFIG_YAML);
        if (!Files.exists(configPath)) {
            log.warn("{}: config file not found: {}", name(), configPath);
            return;
        }
        Map<String, Object> configYaml = YmlLoader.loadYamlAsMap(configPath.toString());
        GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(ConfigUtil.getLagiYmlPath().toString(), GlobalConfigurations.class);
        List<Backend> backends = globalConfigurations.getFunctions().getChat().getBackends();
        List<Backend> hermesBackends = backends.stream()
                .filter(backend -> backend.getBackend() != null && backend.getBackend().startsWith(getNamePrefix()))
                .collect(Collectors.toList());

        if (hermesBackends.isEmpty()) {
            return;
        }

        Map<String, Object> modelConfig = (Map<String, Object>) configYaml.get("model");
        if (modelConfig == null) {
            modelConfig = new HashMap<>();
            configYaml.put("model", modelConfig);
        }
        Backend primary = hermesBackends.get(0);
        modelConfig.put("default", primary.getModel());
        modelConfig.put("provider", "custom");
        modelConfig.put("base_url", path);

        String displayName = buildProviderDisplayName(path);
        List<Map<String, Object>> customProviders = (List<Map<String, Object>>) configYaml.get("custom_providers");
        if (customProviders == null) {
            customProviders = new ArrayList<>();
            configYaml.put("custom_providers", customProviders);
        }
        for (Backend backend : hermesBackends) {
            String model = backend.getModel();
            boolean exists = customProviders.stream().anyMatch(entry ->
                    path.equals(entry.get("base_url")) && model.equals(entry.get("model"))
            );
            if (!exists) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", displayName);
                entry.put("base_url", path);
                entry.put("model", model);
                customProviders.add(entry);
            }
        }

        YmlLoader.writeYaml(configPath.toString(), configYaml);
    }

    private String buildProviderDisplayName(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host != null && port > 0) {
                return "Local (" + host + ":" + port + ")";
            }
            if (host != null) {
                return "Local (" + host + ")";
            }
        } catch (Exception e) {
            log.debug("{}: failed to parse URL for display name: {}", name(), url);
        }
        return "Local (LinkMind)";
    }

    @Override
    public void load(String urlPath) {
        Path hermesPath = Paths.get(basePath);
        if (!hermesPath.toFile().exists()) {
            log.warn("{}: {} does not exist, skipping configuration synchronization", name(), hermesPath);
            return;
        }
        if (!hermesPath.toFile().isDirectory()) {
            log.warn("{}: {} is not a directory, skipping configuration synchronization", name(), hermesPath);
            return;
        }
        Path configPath = hermesPath.resolve(CONFIG_YAML);
        if (!Files.exists(configPath)) {
            log.warn("{}: config file not found: {}", name(), configPath);
            return;
        }
        try {
            Map<String, Object> hermesConfig = YmlLoader.loadYamlAsMap(configPath.toString());
            Map<String, Object> modelConfig = (Map<String, Object>) hermesConfig.get("model");
            if (modelConfig == null) {
                log.warn("{}: no model configuration found in config.yaml", name());
                return;
            }

            String exportedUrlAuthority = extractAuthority(urlPath);
            String baseUrl = (String) modelConfig.get("base_url");
            if (exportedUrlAuthority != null && !isBlank(baseUrl)
                    && exportedUrlAuthority.equals(extractAuthority(baseUrl))) {
                log.info("{}: model base_url {} points to LinkMind itself, skipping load", name(), baseUrl);
                return;
            }

            Path envPath = hermesPath.resolve(ENV_FILE);
            Map<String, String> envKeyValues = loadEnv(envPath);

            Backend backend = convertModelConfig2Backend(modelConfig, envKeyValues);
            if (backend == null) {
                log.warn("{}: failed to build backend from config", name());
                return;
            }

            GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(ConfigUtil.getLagiYmlPath().toString(), GlobalConfigurations.class);
            ConfigUtil.setConfig(globalConfigurations, Collections.singletonList(backend));
            YmlLoader.writeYaml(ConfigUtil.getLagiYmlPath().toString(), globalConfigurations);
        } catch (Exception e) {
            log.error("{}: failed to load and sync config", name(), e);
        }
    }

    @Override
    public String name() {
        return "Hermes";
    }

    private String getNamePrefix() {
        return "linkmind-" + name().toLowerCase();
    }

    private Backend convertModelConfig2Backend(Map<String, Object> modelConfig, Map<String, String> envKeyValues) {
        String model = (String) modelConfig.get("default");
        String provider = (String) modelConfig.get("provider");
        String baseUrl = (String) modelConfig.get("base_url");

        if (isBlank(model)) {
            log.warn("{}: model.default is not configured", name());
            return null;
        }
        if (isBlank(provider)) {
            log.warn("{}: model.provider is not configured", name());
            return null;
        }

        try {
            String backendName = "linkmind-" + name().toLowerCase() + "-" + provider;
            Backend backend = new Backend();
            backend.setName(backendName);
            backend.setType(backendName);
            backend.setBackend(backendName);
            backend.setModel(model);
            backend.setEnable(true);
            backend.setStream(true);
            backend.setPriority(100);

            String driver = PROVIDER_DRIVER_MAP.getOrDefault(provider, "ai.llm.adapter.impl.OpenAIStandardAdapter");
            backend.setDriver(driver);

            String baseUrlEnvKey = PROVIDER_BASE_URL_KEY_MAP.get(provider);
            if (baseUrlEnvKey != null && envKeyValues.containsKey(baseUrlEnvKey)) {
                baseUrl = envKeyValues.get(baseUrlEnvKey);
            }
            if (!isBlank(baseUrl)) {
                backend.setEndpoint(toCompletionApiAddress(baseUrl));
                backend.setApiAddress(toCompletionApiAddress(baseUrl));
            }

            String apiKeyEnvKey = PROVIDER_API_KEY_MAP.get(provider);
            if (apiKeyEnvKey != null) {
                String apiKey = envKeyValues.get(apiKeyEnvKey);
                backend.setApiKey(apiKey);
            }

            return backend;
        } catch (Exception e) {
            log.error("{}: failed to convert model config to backend", name(), e);
        }
        return null;
    }

    private Map<String, String> loadEnv(Path envPath) {
        Map<String, String> envMap = new HashMap<>();
        if (!Files.exists(envPath)) {
            return envMap;
        }
        try {
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
        } catch (IOException e) {
            log.error("{}: failed to load .env file: {}", name(), envPath, e);
        }
        return envMap;
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

    private String extractAuthority(String url) {
        if (isBlank(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) {
                return null;
            }
            return port > 0 ? host + ":" + port : host;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
