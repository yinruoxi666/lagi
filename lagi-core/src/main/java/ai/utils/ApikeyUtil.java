package ai.utils;

import ai.common.pojo.Response;
import ai.config.ContextLoader;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ApikeyUtil {
    private static final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(ApikeyUtil.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SAAS_API_KEY_VALIDATE_PATH = "/saas/api/apikey/validate";
    private static final int DEFAULT_TIMEOUT_SECONDS = 3;
    private static final Boolean enableAuth = ContextLoader.configuration.getFunctions().getChat().getEnableAuth();

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String apiKey = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return apiKey.isEmpty() ? null : apiKey;
    }

    public static boolean isApiKeyValid(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", BEARER_PREFIX + apiKey.trim());
        boolean result = false;
        try {
            String json = OkHttpUtil.post(AiGlobal.SAAS_URL + "/isApiKeyValid", headers, new HashMap<>(), "", DEFAULT_TIMEOUT_SECONDS);
            Response response = gson.fromJson(json, Response.class);
            if (response != null && "success".equals(response.getStatus())) {
                result = true;
            }
        } catch (Exception e) {
            logger.error("Landing Apikey check error", e);
        }
        return result;
    }

    public static boolean validateModelApiKey(String apiKey) {
        if (!enableAuth) {
            return true;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("apiKey", apiKey.trim());
        String jsonBody = gson.toJson(requestBody);
        try {
            String json = OkHttpUtil.post(AiGlobal.SAAS_URL + SAAS_API_KEY_VALIDATE_PATH, new HashMap<>(), new HashMap<>(), jsonBody, DEFAULT_TIMEOUT_SECONDS);
            Response response = gson.fromJson(json, Response.class);
            return response != null && "success".equalsIgnoreCase(response.getStatus());
        } catch (Exception e) {
            logger.error("ApiKey validate api check error", e);
            return false;
        }
    }
}
