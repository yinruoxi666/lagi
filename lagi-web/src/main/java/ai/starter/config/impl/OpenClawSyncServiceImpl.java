package ai.starter.config.impl;

import ai.common.pojo.Backend;
import ai.config.GlobalConfigurations;
import ai.starter.OpenClawInjector;
import ai.starter.OpenClawUtil;
import ai.starter.config.util.ConfigUtil;
import ai.utils.YmlLoader;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class OpenClawSyncServiceImpl extends BaseSyncServiceImpl {

    private static final String OPENCLAW_JSON = "openclaw.json";
    private static final String MODELS_JSON = "agents/main/agent/models.json";
    private static final String AUTH_JSON = "agents/main/agent/auth-profiles.json";

    private final Properties openClawJsonProperties = new Properties();
    private final Properties modelsJsonProperties = new Properties();
    private final Properties authJsonProperties = new Properties();

    public OpenClawSyncServiceImpl(String basePath) {
        super(basePath);
        Path openClawBathPath = OpenClawUtil.getOpenClawBathPath();
        if(openClawBathPath != null && StrUtil.isNotBlank(this.basePath)) {
            this.basePath = openClawBathPath.toString();
        }
        if (openClawBathPath != null) {
            Map<String, Object> map = YmlLoader.readYamlAsMap(openClawBathPath.resolve(OPENCLAW_JSON).toString());
            YmlLoader.flattenYamlToProperties(map, "", openClawJsonProperties);
            map = YmlLoader.readYamlAsMap(openClawBathPath.resolve(MODELS_JSON).toString());
            YmlLoader.flattenYamlToProperties(map, "", modelsJsonProperties);
            map = YmlLoader.readYamlAsMap(openClawBathPath.resolve(AUTH_JSON).toString());
            YmlLoader.flattenYamlToProperties(map, "", authJsonProperties);
        }
    }

    @Override
    public boolean check() {
        return OpenClawUtil.openClawExists();
    }

    @Override
    public void export(String path) {
        OpenClawInjector.inject(path);
    }

    @Override
    public void load() {
        Path lagiYmlPath = ConfigUtil.getLagiYmlPath();
        if(lagiYmlPath == null || !lagiYmlPath.toFile().exists()) {
            log.error("{}: \t Lagi.yml not found", name());
            return;
        }
        GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(lagiYmlPath.toString(), GlobalConfigurations.class);
        if(globalConfigurations == null) {
            log.error("{}: \t Lagi.yml parse failed", name());
            return;
        }
        Backend openClawBackend;
        try {
            openClawBackend = loadBackend();
        } catch (Exception e) {
            log.error("{}: \t Failed to load OpenClaw config", name(), e);
            return;
        }
        ConfigUtil.setConfig(globalConfigurations, Collections.singletonList(openClawBackend));
        YmlLoader.writeYaml(ConfigUtil.getLagiYmlPath().toString(), globalConfigurations);
    }


    @Override
    public String name() {
        return "OpenClaw";
    }

    private String provider2Driver(String provider) {
        if("qwen".equals(provider)) {
            return "ai.llm.adapter.impl.QwenAdapter";
        } else if("zai".equals(provider)) {
            return "ai.llm.adapter.impl.ZhipuAdapter";
        } else if("moonshot".equals(provider)) {
            return "ai.llm.adapter.impl.MoonshotAdapter";
        } else if("openrouter".equals(provider)) {
            return "ai.llm.adapter.impl.OpenRouterAdapter";
        }
        return "ai.llm.adapter.impl.OpenAIStandardAdapter";
    }

    private String provider2Protocol(String provider) {
        if("qwen".equals(provider)) {
            return "response";
        }
        return "completion";
    }

    public Backend loadBackend() {
        String primary = (String) openClawJsonProperties.get("agents.defaults.model.primary");
        String[] info = primary.split("/");
        if(info.length != 2) {
            throw new RuntimeException("Invalid model primary");
        }
        String provider = info[0];
        String model = info[info.length - 1];
        Backend backend = new Backend();
        backend.setDriver(provider2Driver(provider));
        backend.setModel(model);
        backend.setName(name().toLowerCase() + "-" + provider);
        backend.setEnable(true);
        backend.setStream(true);
        backend.setPriority(100);
        backend.setType(name().toLowerCase());
        backend.setProtocol(provider2Protocol(provider));
        String apiKey = (String) modelsJsonProperties.get(StrUtil.format("providers.{}:apiKey", provider));
        String authApiKey = StrUtil.format("{}_api_key",  provider);
        if(apiKey != null && !apiKey.toLowerCase().equals(authApiKey)) {
            backend.setApiKey(apiKey);
        } else {
            String providerAuthApiKey = StrUtil.format("profiles.{}:default.key", provider);
            apiKey =  (String) authJsonProperties.get(providerAuthApiKey);
            backend.setApiKey(apiKey);
        }
        if("ai.llm.adapter.impl.OpenAIStandardAdapter".equals(backend.getDriver())) {
            String baseUrl = (String) modelsJsonProperties.get(StrUtil.format("providers.{}.baseUrl", provider));
            if(baseUrl != null) {
                backend.setEndpoint(baseUrl + "/chat/completions");
                backend.setApiAddress(baseUrl + "/chat/completions");
            } else {
                baseUrl = (String) openClawJsonProperties.get(StrUtil.format("models.providers.{}.baseUrl", provider));
                if(baseUrl != null) {
                    backend.setEndpoint(baseUrl + "/chat/completions");
                    backend.setApiAddress(baseUrl + "/chat/completions");
                }
            }
        }
        backend.setApiKey(apiKey);
        return backend;
    }

    public static void main(String[] args) {
        OpenClawSyncServiceImpl openClawSyncService = new OpenClawSyncServiceImpl("C:\\Users\\Administrator\\..openclaw");
//        openClawSyncService.loadBackend();
        System.out.println(openClawSyncService.loadBackend());
    }


}
