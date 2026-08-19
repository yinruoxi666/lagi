package ai.vector.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaHttpPerformanceEventListenerTest {
    private static final String TOKEN = "test-token-must-not-appear";
    private static final String VECTOR = "[0.123456,0.654321]";
    private static final String RESPONSE_BODY = "response-body-must-not-appear";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(VectorSearchPerformanceContext.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsSafeNetworkBreakdownReuseSlowResponseHttpErrorAndConnectionFailure() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ids\":[]}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ids\":[]}"));
        server.enqueue(new MockResponse().setResponseCode(503)
                .setHeadersDelay(550L, TimeUnit.MILLISECONDS)
                .setBody(RESPONSE_BODY));
        server.start();
        MockWebServer failureServer = new MockWebServer();
        failureServer.start();
        Request failureRequest = request(failureServer,
                "/api/v2/tenants/default/databases/default/collections/c1/get");
        failureServer.shutdown();

        OkHttpClient client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .readTimeout(2L, TimeUnit.SECONDS)
                .eventListenerFactory(call -> {
                    VectorSearchPerformanceContext.Snapshot snapshot = VectorSearchPerformanceContext.capture();
                    return snapshot == null
                            ? okhttp3.EventListener.NONE
                            : new ChromaHttpPerformanceEventListener(snapshot);
                })
                .build();

        try (VectorSearchPerformanceContext.Scope ignored =
                     VectorSearchPerformanceContext.open("chroma-listener-test")) {
            execute(client, request(server, "/api/v2/tenants/default/databases/default/collections/c1/query"));
            execute(client, request(server, "/api/v2/tenants/default/databases/default/collections/c1/get"));
            execute(client, request(server, "/api/v2/tenants/default/databases/default/collections/c1/query"));
            assertThrows(IOException.class, () -> execute(client, failureRequest));
        } finally {
            server.shutdown();
        }

        String allMessages = allMessages();
        assertTrue(allMessages.contains("operation=query"));
        assertTrue(allMessages.contains("operation=get"));
        assertTrue(allMessages.contains("statusCode=503"));
        assertTrue(allMessages.contains("连接复用=true"));
        assertTrue(allMessages.contains("状态=error"));
        assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("Chroma HTTP query")));
        assertFalse(allMessages.contains(TOKEN));
        assertFalse(allMessages.contains(VECTOR));
        assertFalse(allMessages.contains(RESPONSE_BODY));
        assertFalse(allMessages.contains("Authorization"));
    }

    private Request request(MockWebServer server, String path) {
        return new Request.Builder()
                .url(server.url(path + "?api_key=" + TOKEN))
                .header("Authorization", "Bearer " + TOKEN)
                .post(RequestBody.create("{\"query_embeddings\":" + VECTOR + "}",
                        MediaType.get("application/json")))
                .build();
    }

    private void execute(OkHttpClient client, Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                response.body().string();
            }
        }
    }

    private String allMessages() {
        StringBuilder messages = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            messages.append(event.getFormattedMessage()).append('\n');
        }
        return messages.toString();
    }
}
