package ai.migrate.service;

import ai.config.ContextLoader;
import ai.config.pojo.GeneralConfig;
import ai.dto.LagiModelInfo;
import ai.dto.ModelApiKey;
import ai.migrate.dao.ApiKeyDao;
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
    private static final String LANDING = "Landing";
    private String lagiYmlPath = null;

    private static final String SAAS_BASE_URL = AiGlobal.SAAS_URL;
    private final Gson gson = new Gson();
    private final ApiKeyDao apiKeyDao = new ApiKeyDao();

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

    /**
     * When true (default), API key UI manages local DB keys plus remote Landing keys.
     * When false, only remote Landing keys are listed or mutated.
     */
    public boolean isLocalApiKeyEditable() {
        try {
            if (ContextLoader.configuration == null) {
                return true;
            }
            GeneralConfig general = ContextLoader.configuration.getGeneral();
            if (general == null) {
                return true;
            }
            Boolean b = general.getLocalApiKeyEditable();
            return b == null || Boolean.TRUE.equals(b);
        } catch (Exception e) {
            return true;
        }
    }

    public List<ModelApiKey> listApiKeys(String userId) throws IOException {
        List<ModelApiKey> all = new ArrayList<>();
        if (!isBlank(userId)) {
            all.addAll(getLandingApiKeys(userId));
        }
        if (isLocalApiKeyEditable()) {
            try {
                all.addAll(apiKeyDao.listAll());
            } catch (Exception e) {
                throw new IOException("list local api keys failed: " + e.getMessage(), e);
            }
        }
        return all;
    }

    public synchronized List<ModelApiKey> getLocalApiKeys() throws IOException {
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        List<ModelApiKey> result = new ArrayList<ModelApiKey>();
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
            ModelApiKey row = new ModelApiKey();
            row.setName(nameObj.toString());
            row.setProvider(provider);
            row.setApiKey(apiKey);
            row.setApiAddress(apiAddress);
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

    public synchronized void deleteApiKey(Long id, String provider, String userId) throws IOException {
        if (id == null) {
            throw new IOException("id is required");
        }
        String p = normalizeProvider(provider);
        if (LANDING.equalsIgnoreCase(p)) {
            deleteLandingApiKey(id);
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }
        try {
            ModelApiKey item = apiKeyDao.findById(id);
            if (item != null && item.getStatus() != null && item.getStatus() == 1) {
                resetProviderApiKeyInConfig(item.getProvider());
            }
            apiKeyDao.deleteById(id);
        } catch (Exception e) {
            throw new IOException("delete local api key failed: " + e.getMessage(), e);
        }
    }

    public synchronized List<String> listProviders() throws IOException {
        if (!isLocalApiKeyEditable()) {
            return Collections.singletonList(LANDING);
        }
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
        if (isBlank(name)) {
            throw new IOException("name is required");
        }
        String normalizedProvider = normalizeProvider(provider);
        if (isBlank(apiKey) && !LANDING.equalsIgnoreCase(normalizedProvider)) {
            throw new IOException("apiKey is required");
        }
        String nameTrimmed = name.trim();

        if (LANDING.equalsIgnoreCase(normalizedProvider)) {
            ModelApiKey landingRequest = new ModelApiKey();
            // Display label for SAAS; lagi.yml model "name" is never updated from this field.
            landingRequest.setName(nameTrimmed);
            landingRequest.setApiKey(isBlank(apiKey) ? null : apiKey.trim());
            landingRequest.setStatus(1);
            if (!isBlank(userId)) {
                landingRequest.setUserId(userId.trim());
            }
            addLandingApiKey(landingRequest);
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }

        ModelApiKey local = new ModelApiKey();
        // Stored in api_key_store only; applyProviderApiKeyToConfig does not write this to lagi.yml.
        local.setName(nameTrimmed);
        local.setProvider(normalizedProvider);
        local.setApiKey(apiKey.trim());
        local.setApiAddress(isBlank(apiAddress) ? "" : apiAddress.trim());
        local.setStatus(0);
        try {
            apiKeyDao.insert(local);
        } catch (Exception e) {
            throw new IOException("add local api key failed: " + e.getMessage(), e);
        }
    }

    public synchronized void toggleApiKey(Long id, String provider, boolean enabled, String userId) throws IOException {
        if (id == null) {
            throw new IOException("id is required");
        }
        String p = normalizeProvider(provider);
        if (LANDING.equalsIgnoreCase(p)) {
            if (isBlank(userId)) {
                throw new IOException("userId is required for Landing");
            }
            List<ModelApiKey> list = getLandingApiKeys(userId);
            ModelApiKey target = null;
            for (ModelApiKey item : list) {
                if (item.getId() != null && item.getId().equals(id)) {
                    target = item;
                    break;
                }
            }
            if (target == null) {
                throw new IOException("Landing api key not found");
            }
            if (enabled) {
                applyProviderApiKeyToConfig(LANDING, target.getApiKey());
            } else {
                resetProviderApiKeyInConfig(LANDING);
            }
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }

        try {
            ModelApiKey target = apiKeyDao.findById(id);
            if (target == null) {
                throw new IOException("Local api key not found");
            }
            if (enabled) {
                apiKeyDao.disableProviderKeys(target.getProvider());
                apiKeyDao.setStatusById(id, 1);
                applyProviderApiKeyToConfig(target.getProvider(), target.getApiKey());
            } else {
                apiKeyDao.setStatusById(id, 0);
                resetProviderApiKeyInConfig(target.getProvider());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("toggle local api key failed: " + e.getMessage(), e);
        }
    }

    public ModelApiKey addLandingApiKey(ModelApiKey requestBody) throws IOException {
        if (requestBody == null) {
            throw new IOException("request body is required");
        }
        String url = SAAS_BASE_URL + "/saas/api/apikey/addApiKey";
        String requestJson = gson.toJson(requestBody);
        String resultJson = OkHttpUtil.post(url, requestJson);
        ModelApiKey response = gson.fromJson(resultJson, ModelApiKey.class);
        return response;
    }

    /**
     * Deletes a Landing API key on SAAS (POST /saas/api/apikey/deleteModelApiKey, body: {"id":...}).
     */
    public void deleteLandingApiKey(Long id) throws IOException {
        if (id == null) {
            throw new IOException("id is required");
        }
        String url = SAAS_BASE_URL + "/saas/api/apikey/deleteModelApiKey";
        Map<String, Long> payload = new LinkedHashMap<String, Long>();
        payload.put("id", id);
        String requestJson = gson.toJson(payload);
        OkHttpUtil.post(url, requestJson);
    }

    /**
     * Writes only api_key (and Landing model list) into lagi.yml. Never updates the model entry's
     * {@code name} field — user-provided key labels stay in DB / SAAS only.
     */
    private void applyProviderApiKeyToConfig(String provider, String apiKey) throws IOException {
        if (isBlank(provider) || isBlank(apiKey)) {
            throw new IOException("provider/apiKey is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByProvider(getModels(root), provider);
        if (model == null) {
            throw new IOException("Provider not found in models: " + provider);
        }
        model.put("api_key", apiKey.trim());
        if (LANDING.equalsIgnoreCase(provider)) {
            model.put("model", getLandingModels());
        }
        writeRootYaml(root);
    }

    private void resetProviderApiKeyInConfig(String provider) throws IOException {
        if (isBlank(provider)) {
            throw new IOException("provider is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByProvider(getModels(root), provider);
        if (model == null) {
            throw new IOException("Provider not found in models: " + provider);
        }
        model.put("api_key", "your-api-key");
        writeRootYaml(root);
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

    public List<ModelApiKey> getLandingApiKeys(String userId) throws IOException {
        String url = SAAS_BASE_URL + "/saas/api/apikey/listModelApiKeys";
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        String resultJson = OkHttpUtil.get(url, params);
        Type listType = new TypeToken<ArrayList<ModelApiKey>>() {
        }.getType();
        List<ModelApiKey> list = gson.fromJson(resultJson, listType);
        if (list != null) {
            list.forEach(item -> item.setProvider("Landing"));
        }
        return list != null ? list : new ArrayList<>();
    }
}
