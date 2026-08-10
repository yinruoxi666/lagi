package ai.rerank.client;

import ai.rerank.adapter.RerankAdapterResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BailianRerankClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsQwen3RequestAndMapsResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"rerank-id\","
                + "\"model\":\"qwen3-rerank\","
                + "\"usage\":{\"total_tokens\":42},"
                + "\"results\":["
                + "{\"index\":1,\"relevance_score\":0.91},"
                + "{\"index\":0,\"relevance_score\":0.55}]}"));
        BailianRerankClient client = client();

        RerankAdapterResult result = client.rerank("轮椅旅客", Arrays.asList("文档一", "文档二"));

        assertEquals("rerank-id", result.getId());
        assertEquals("qwen3-rerank", result.getModel());
        assertEquals(Integer.valueOf(42), result.getTotalTokens());
        assertEquals(Integer.valueOf(2), result.getProcessedDocuments());
        assertEquals(Integer.valueOf(1), result.getResults().get(0).getIndex());
        assertEquals(Double.valueOf(0.91d), result.getResults().get(0).getRelevanceScore());

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
        assertEquals("/compatible-api/v1/reranks", request.getPath());
        JsonNode requestJson = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals("qwen3-rerank", requestJson.get("model").asText());
        assertEquals("Retrieve semantically similar text.", requestJson.get("instruct").asText());
        assertEquals(2, requestJson.get("top_n").asInt());
        assertEquals(2, requestJson.get("documents").size());
    }

    @Test
    void rejectsHttpErrorsWithoutReturningPartialResults() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{"
                + "\"code\":\"InvalidApiKey\","
                + "\"message\":\"invalid credentials\","
                + "\"request_id\":\"request-1\"}"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one")));
    }

    @Test
    void rejectsServerErrorsWithoutReturningPartialResults() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{"
                + "\"code\":\"ServiceUnavailable\","
                + "\"message\":\"try later\","
                + "\"request_id\":\"request-2\"}"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one")));
    }

    @Test
    void rejectsIncompleteResponses() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"results\":[{\"index\":0,\"relevance_score\":0.8}]}"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one", "two")));
    }

    @Test
    void rejectsMalformedResponses() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not-json"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one")));
    }

    @Test
    void rejectsDuplicateIndices() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"results\":["
                + "{\"index\":0,\"relevance_score\":0.8},"
                + "{\"index\":0,\"relevance_score\":0.7}]}"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one", "two")));
    }

    @Test
    void rejectsOutOfRangeIndices() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"results\":[{\"index\":1,\"relevance_score\":0.8}]}"));

        assertThrows(IllegalStateException.class,
                () -> client().rerank("query", Arrays.asList("one")));
    }

    @Test
    void propagatesReadTimeouts() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        OkHttpClient timeoutClient = new OkHttpClient.Builder()
                .readTimeout(100, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();

        assertThrows(IllegalStateException.class,
                () -> client(timeoutClient).rerank("query", Arrays.asList("one")));
    }

    @Test
    void buildsOfficialBeijingWorkspaceEndpoint() {
        assertEquals("https://workspace-1.cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks",
                BailianRerankClient.buildEndpoint("workspace-1"));
    }

    private BailianRerankClient client() {
        return client(new OkHttpClient.Builder().retryOnConnectionFailure(false).build());
    }

    private BailianRerankClient client(OkHttpClient httpClient) {
        return new BailianRerankClient(
                server.url("/compatible-api/v1/reranks").toString(),
                "test-api-key",
                httpClient);
    }
}
