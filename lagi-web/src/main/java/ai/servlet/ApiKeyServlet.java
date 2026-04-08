package ai.servlet;

import ai.migrate.service.ApiKeyService;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiKeyServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final ApiKeyService apiKeyService = new ApiKeyService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "list":
                this.list(req, resp);
                break;
            case "get":
                this.get(req, resp);
                break;
            case "providers":
                this.providers(req, resp);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "add":
                this.add(req, resp);
                break;
            case "update":
                this.update(req, resp);
                break;
            case "delete":
                this.delete(req, resp);
                break;
        }
    }

    private void list(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, String>> data = apiKeyService.listApiKeys();
            for (Map<String, String> item : data) {
                if (item != null) {
                    item.put("api_key", maskApiKey(item.get("api_key")));
                }
            }
            result.put("status", "success");
            result.put("data", data);
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        String modelName = req.getParameter("modelName");
        try {
            Map<String, String> data = apiKeyService.getApiKey(modelName);
            if (data == null) {
                result.put("status", "failed");
                result.put("msg", "Model not found");
            } else {
                data.put("api_key", maskApiKey(data.get("api_key")));
                result.put("status", "success");
                result.put("data", data);
            }
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void providers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> data = apiKeyService.listProviders();
            result.put("status", "success");
            result.put("data", data);
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    @SuppressWarnings("unchecked")
    private void add(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> body = reqBodyToObj(req, Map.class);
            String modelName = body == null || body.get("name") == null ? null : body.get("name").toString();
            String provider = body == null || body.get("provider") == null ? null : body.get("provider").toString();
            String apiKey = body == null || body.get("apiKey") == null ? null : body.get("apiKey").toString();
            String model = body == null || body.get("model") == null ? null : body.get("model").toString();
            String apiAddress = body == null || body.get("apiAddress") == null ? null : body.get("apiAddress").toString();
            String userId = body == null || body.get("userId") == null ? null : body.get("userId").toString();
            apiKeyService.addApiKey(modelName, provider, apiKey, model, apiAddress, userId);
            result.put("status", "success");
            result.put("msg", "add success");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    @SuppressWarnings("unchecked")
    private void update(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> body = reqBodyToObj(req, Map.class);
            String modelName = body == null || body.get("modelName") == null ? null : body.get("modelName").toString();
            String apiKey = body == null || body.get("apiKey") == null ? null : body.get("apiKey").toString();
            apiKeyService.saveApiKey(modelName, apiKey);
            result.put("status", "success");
            result.put("msg", "update success");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    @SuppressWarnings("unchecked")
    private void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> body = reqBodyToObj(req, Map.class);
            String modelName = body == null || body.get("modelName") == null ? null : body.get("modelName").toString();
            apiKeyService.deleteApiKey(modelName);
            result.put("status", "success");
            result.put("msg", "delete success");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        int len = apiKey.length();
        if (len <= 4) {
            return "****";
        }
        if (len <= 8) {
            return apiKey.substring(0, 1) + "****" + apiKey.substring(len - 1);
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(len - 4);
    }
}
