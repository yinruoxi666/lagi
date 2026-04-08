package ai.migrate.service;

import ai.config.ContextLoader;
import ai.dto.LagiModelInfo;
import ai.dto.ModelApiKey;
import ai.utils.AiGlobal;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ApiKeyService {
    private static final String OPENAI_COMPATIBLE = "OpenAICompatible";
    private String lagiYmlPath = null;

    private static final String SAAS_BASE_URL = AiGlobal.SAAS_URL;
    private final Gson gson = new Gson();

    private String getLagiYmlPath() {
        if (lagiYmlPath == null) {
            String configFile = System.getProperty(ai.starter.InstallerUtil.CONFIG_FILE_PROPERTY);
            if (configFile != null && !configFile.isEmpty()) {
                File f = new File(configFile);
                if (f.exists() && f.isFile()) {
                    lagiYmlPath = configFile;
                    return lagiYmlPath;
                }
            }
            String userDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                    userDir + "/lagi-web/src/main/resources/lagi.yml",
                    userDir + "/src/main/resources/lagi.yml",
                    "../lagi-web/src/main/resources/lagi.yml",
                    userDir + "/WEB-INF/classes/lagi.yml",
                    "lagi.yml"
            };
            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    lagiYmlPath = path;
                    break;
                }
            }
        }
        return lagiYmlPath;
    }

    private String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    private Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRootYaml() throws IOException {
        String path = getLagiYmlPath();
        if (path == null) {
            throw new IOException("Cannot find lagi.yml");
        }
        String encoding = detectEncoding(path);
        if (encoding == null || encoding.trim().isEmpty()) {
            encoding = "UTF-8";
        }
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        Object loaded = createYaml().load(new String(bytes, Charset.forName(encoding)));
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        }
        return new LinkedHashMap<>();
    }

    private void writeRootYaml(Map<String, Object> root) throws IOException {
        String path = getLagiYmlPath();
        if (path == null) {
            throw new IOException("Cannot find lagi.yml");
        }
        OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(Paths.get(path)),
                StandardCharsets.UTF_8
        );
        try {
            createYaml().dump(root, writer);
        } finally {
            writer.close();
        }
        String writtenPath = getLagiYmlPath();
        if (writtenPath != null) {
            ContextLoader.reloadLagiYmlFromFile(writtenPath);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getModels(Map<String, Object> root) {
        Object modelsObj = root.get("models");
        if (!(modelsObj instanceof List)) {
            return new ArrayList<Map<String, Object>>();
        }
        List<?> raw = (List<?>) modelsObj;
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        for (Object item : raw) {
            if (item instanceof Map) {
                models.add((Map<String, Object>) item);
            }
        }
        return models;
    }

    private Map<String, Object> findModelByName(List<Map<String, Object>> models, String modelName) {
        for (Map<String, Object> model : models) {
            Object name = model.get("name");
            if (name != null && modelName.equals(name.toString().trim())) {
                return model;
            }
        }
        return null;
    }

    private Map<String, Object> findModelByProvider(List<Map<String, Object>> models, String provider) {
        for (Map<String, Object> model : models) {
            Object typeObj = model.get("type");
            if (typeObj != null && provider.equalsIgnoreCase(typeObj.toString().trim())) {
                return model;
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isApiKeyUsable(String apiKey) {
        if (isBlank(apiKey)) {
            return false;
        }
        String value = apiKey.trim();
        return !("your-api-key".equalsIgnoreCase(value));
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        String p = provider.trim();
        if (OPENAI_COMPATIBLE.equalsIgnoreCase(p)) {
            return OPENAI_COMPATIBLE;
        }
        return p;
    }

    public synchronized List<Map<String, String>> listApiKeys() throws IOException {
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        for (Map<String, Object> model : models) {
            Object nameObj = model.get("name");
            if (nameObj == null) {
                continue;
            }
            String provider = normalizeProvider(model.get("type") == null ? "" : model.get("type").toString());
            Object apiKeyObj = model.get("api_key");
            String apiKey = apiKeyObj == null ? "" : apiKeyObj.toString();
            if (!isApiKeyUsable(apiKey)) {
                continue;
            }
            Object apiAddressObj = model.get("api_address");
            String apiAddress = apiAddressObj == null ? "" : apiAddressObj.toString();
            if (!OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
                apiAddress = "";
            }
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("name", nameObj.toString());
            row.put("provider", provider);
            row.put("api_key", apiKey);
            row.put("api_address", apiAddress);
            result.add(row);
        }
        return result;
    }

    public synchronized Map<String, String> getApiKey(String modelName) throws IOException {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IOException("modelName is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByName(getModels(root), modelName.trim());
        if (model == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        Object typeObj = model.get("type");
        String provider = normalizeProvider(typeObj == null ? "" : typeObj.toString());
        Object apiKeyObj = model.get("api_key");
        Object apiAddressObj = model.get("api_address");
        String apiKey = apiKeyObj == null ? "" : apiKeyObj.toString();
        if (!isApiKeyUsable(apiKey)) {
            return null;
        }
        String apiAddress = apiAddressObj == null ? "" : apiAddressObj.toString();
        if (!OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
            apiAddress = "";
        }
        result.put("name", modelName.trim());
        result.put("provider", provider);
        result.put("api_key", apiKey);
        result.put("api_address", apiAddress);
        return result;
    }

    public synchronized void saveApiKey(String modelName, String apiKey) throws IOException {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IOException("modelName is required");
        }
        if (apiKey == null) {
            throw new IOException("apiKey is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByName(getModels(root), modelName.trim());
        if (model == null) {
            throw new IOException("Model not found: " + modelName);
        }
        model.put("api_key", apiKey);
        writeRootYaml(root);
    }

    public synchronized void deleteApiKey(String modelName) throws IOException {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IOException("modelName is required");
        }
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        Map<String, Object> model = findModelByName(models, modelName.trim());
        if (model == null) {
            throw new IOException("Model not found: " + modelName);
        }
        String provider = normalizeProvider(model.get("type") == null ? "" : model.get("type").toString());
        String name = model.get("name") == null ? "" : model.get("name").toString().trim();

        // For OpenAICompatible custom extensions, remove the model item directly.
        if (OPENAI_COMPATIBLE.equalsIgnoreCase(provider) && !"custom".equalsIgnoreCase(name)) {
            models.remove(model);
            root.put("models", models);
        } else {
            model.remove("api_key");
        }
        writeRootYaml(root);
    }

    public synchronized List<String> listProviders() throws IOException {
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        Set<String> providerSet = new HashSet<>();
        for (Map<String, Object> model : models) {
            Object typeObj = model.get("type");
            if (typeObj == null) {
                continue;
            }
            String provider = normalizeProvider(typeObj.toString());
            if (!isBlank(provider)) {
                providerSet.add(provider);
            }
        }
        List<String> result = new ArrayList<String>(providerSet);
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    public synchronized void addApiKey(String name, String provider, String apiKey, String modelValue, String apiAddress, String userId) throws IOException {
        if (isBlank(provider)) {
            throw new IOException("provider is required");
        }
        String normalizedProvider = normalizeProvider(provider);
        if (isBlank(apiKey) && !"Landing".equalsIgnoreCase(normalizedProvider)) {
            throw new IOException("apiKey is required");
        }
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);

        if (OPENAI_COMPATIBLE.equalsIgnoreCase(normalizedProvider)) {
            if (isBlank(name)) {
                throw new IOException("name is required for OpenAICompatible");
            }
            if (isBlank(modelValue)) {
                throw new IOException("model is required for OpenAICompatible");
            }
            if (findModelByName(models, name.trim()) != null) {
                throw new IOException("Model name already exists: " + name);
            }
            Map<String, Object> newModel = new LinkedHashMap<String, Object>();
            newModel.put("name", name.trim());
            newModel.put("type", OPENAI_COMPATIBLE);
            newModel.put("enable", true);
            newModel.put("model", modelValue.trim());
            newModel.put("driver", "ai.llm.adapter.impl.OpenAIStandardAdapter");
            if (!isBlank(apiAddress)) {
                newModel.put("api_address", apiAddress.trim());
            }
            newModel.put("api_key", apiKey.trim());
            models.add(newModel);
            root.put("models", models);
            writeRootYaml(root);
            return;
        }

        Map<String, Object> target = findModelByProvider(models, normalizedProvider);
        if (target == null) {
            throw new IOException("Provider not found in models: " + normalizedProvider);
        }

        if ("Landing".equalsIgnoreCase(normalizedProvider)) {
            ModelApiKey landingRequest = new ModelApiKey();
            if (!isBlank(name)) {
                landingRequest.setName(name.trim());
            } else if (target.get("name") != null) {
                landingRequest.setName(target.get("name").toString().trim());
            }
            if (!isBlank(apiKey)) {
                landingRequest.setApiKey(apiKey.trim());
            }
            if (!isBlank(userId)) {
                landingRequest.setUserId(userId.trim());
            }
            ModelApiKey landingResponse = addLandingApiKey(landingRequest);
            target.put("api_key", landingResponse.getApiKey());
            target.put("model", getLandingModels());
            writeRootYaml(root);
        } else {
            target.put("api_key", apiKey.trim());
            writeRootYaml(root);
        }
    }

    public ModelApiKey addLandingApiKey(ModelApiKey requestBody) throws IOException {
        if (requestBody == null) {
            throw new IOException("request body is required");
        }
        String url = SAAS_BASE_URL + "/saas/api/apikey/addOrUpdateApiKey";
        String requestJson = gson.toJson(requestBody);
        String resultJson = OkHttpUtil.post(url, requestJson);
        ModelApiKey response = gson.fromJson(resultJson, ModelApiKey.class);
        return response;
    }

    public String getLandingModels() throws IOException {
        List<LagiModelInfo> modelInfoList = listModelInfo();
        StringBuilder models = new StringBuilder();
        for (int i = 0; i < modelInfoList.size(); i++) {
            if (i > 0) {
                models.append(",");
            }
            LagiModelInfo modelInfo = modelInfoList.get(i);
            models.append(modelInfo.getProvider()).append("/").append(modelInfo.getModelName());
        }
        return models.toString();
    }

    public List<LagiModelInfo> listModelInfo() throws IOException {
        String url = SAAS_BASE_URL + "/saas/api/apikey/listModelInfo";
        String resultJson = OkHttpUtil.get(url);
        Type listType = new TypeToken<ArrayList<LagiModelInfo>>() {
        }.getType();
        List<LagiModelInfo> list = gson.fromJson(resultJson, listType);
        return list != null ? list : new ArrayList<>();
    }

}
