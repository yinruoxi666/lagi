package ai.rerank.client;

import ai.rerank.adapter.RerankAdapterResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BailianRerankClient {
    public static final String MODEL_NAME = "qwen3-rerank";
    static final String INSTRUCT = "Retrieve semantically similar text.";

    private static final Logger log = LoggerFactory.getLogger(BailianRerankClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String endpoint;
    private final String apiKey;
    private final OkHttpClient httpClient;

    public BailianRerankClient(String workspaceId, String apiKey) {
        this(buildEndpoint(workspaceId), apiKey, new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build());
    }

    BailianRerankClient(String endpoint, String apiKey, OkHttpClient httpClient) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

    public RerankAdapterResult rerank(String query, List<String> documents) {
        ObjectNode requestJson = OBJECT_MAPPER.createObjectNode();
        requestJson.put("model", MODEL_NAME);
        requestJson.put("query", query);
        requestJson.put("top_n", documents.size());
        requestJson.put("instruct", INSTRUCT);
        ArrayNode documentArray = requestJson.putArray("documents");
        documents.forEach(documentArray::add);

        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(requestJson.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw buildHttpException(response.code(), body);
            }
            return parseResponse(body, documents.size());
        } catch (IOException e) {
            log.warn("Bailian rerank request failed: {}", e.getMessage());
            throw new IllegalStateException("Bailian rerank request failed", e);
        }
    }

    private static RerankAdapterResult parseResponse(String body, int expectedResults) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root == null) {
                throw new IllegalStateException("Bailian rerank returned an empty response");
            }
            JsonNode resultNodes = root.get("results");
            if (resultNodes == null || !resultNodes.isArray()
                    || resultNodes.size() != expectedResults) {
                throw new IllegalStateException("Bailian rerank returned an incomplete result list");
            }

            List<RerankAdapterResult.Result> results = new ArrayList<>();
            Set<Integer> indices = new HashSet<>();
            for (JsonNode resultNode : resultNodes) {
                JsonNode indexNode = resultNode.get("index");
                JsonNode scoreNode = resultNode.get("relevance_score");
                if (indexNode == null || !indexNode.canConvertToInt()
                        || scoreNode == null || !scoreNode.isNumber()) {
                    throw new IllegalStateException("Bailian rerank returned an invalid result item");
                }
                int index = indexNode.asInt();
                if (index < 0 || index >= expectedResults || !indices.add(index)) {
                    throw new IllegalStateException("Bailian rerank returned an invalid result index");
                }
                results.add(RerankAdapterResult.Result.builder()
                        .index(index)
                        .relevanceScore(scoreNode.asDouble())
                        .build());
            }

            JsonNode usageNode = root.path("usage").get("total_tokens");
            Integer totalTokens = usageNode != null && usageNode.canConvertToInt()
                    ? usageNode.asInt() : null;
            return RerankAdapterResult.builder()
                    .id(textOrNull(root.get("id")))
                    .model(defaultIfBlank(textOrNull(root.get("model")), MODEL_NAME))
                    .totalTokens(totalTokens)
                    .processedDocuments(expectedResults)
                    .results(results)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse Bailian rerank response", e);
        }
    }

    private static IllegalStateException buildHttpException(int status, String body) {
        String code = null;
        String message = null;
        String requestId = null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            code = textOrNull(root.get("code"));
            message = textOrNull(root.get("message"));
            requestId = textOrNull(root.get("request_id"));
            if (requestId == null) {
                requestId = textOrNull(root.get("requestId"));
            }
        } catch (Exception ignored) {
            // Do not log the raw response because it may contain request details.
        }
        log.warn("Bailian rerank HTTP error: status={}, code={}, message={}, requestId={}",
                status, code, message, requestId);
        return new IllegalStateException("Bailian rerank returned HTTP " + status);
    }

    static String buildEndpoint(String workspaceId) {
        if (workspaceId == null || !workspaceId.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("A valid Bailian workspace_id is required");
        }
        return "https://" + workspaceId
                + ".cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks";
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
