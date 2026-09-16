package ai.agent.chat.lydaas;

import ai.config.pojo.AgentConfig;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PekIcdhAgentTest {
    private HttpServer server;
    private PekIcdhAgent agent;
    private String sessionId;
    private String conversationId;
    private final LinkedBlockingQueue<JsonObject> requests = new LinkedBlockingQueue<>();
    private final AtomicInteger chatNumber = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        sessionId = "pek-test-" + UUID.randomUUID();
        conversationId = "conversation-" + UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quick/agent/chat/sse", this::respond);
        server.start();
        agent = new PekIcdhAgent(AgentConfig.builder()
                .appId("test-agent")
                .userId("test-tenant")
                .token("Basic test-only")
                .endpoint("http://127.0.0.1:" + server.getAddress().getPort())
                .build());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldOmitDataListForFirstRound() throws InterruptedException {
        assertFalse(send(request(sessionId, true)).has("dataList"));
    }

    @Test
    void shouldSendTheSinglePreviousRound() throws InterruptedException {
        send(request(sessionId, true));
        assertPreviousRound(send(request(sessionId, true)), "chat-1");
    }

    @Test
    void shouldSendOnlyLatestRoundWhenMultipleRoundsAreCached() throws InterruptedException {
        send(request(sessionId, true));
        send(request(sessionId, true));
        ChatCompletionRequest third = request(sessionId, true);
        third.setMessages(Arrays.asList(
                message("user", "earlier question"),
                message("assistant", "earlier answer"),
                message("user", "查一下北京到上海的航班。 ")));

        JsonObject body = send(third);
        assertPreviousRound(body, "chat-2");
        assertEquals("查一下北京到上海的航班。", body.get("query").getAsString());
        assertEquals("test-agent", body.get("agentInstanceId").getAsString());
        assertEquals("test-tenant", body.get("tenantId").getAsString());
        assertEquals("ROBOT", body.get("bizInvokeFrom").getAsString());
        assertTrue(body.get("isStream").getAsBoolean());
        assertFalse(body.has("messages"));
        assertPreviousRound(send(request(sessionId, true)), "chat-3");
    }

    @Test
    void shouldAlsoSendOnlyLatestRoundForNonStreamingRequests() throws InterruptedException {
        send(request(sessionId, false));
        send(request(sessionId, false));
        JsonObject body = send(request(sessionId, false));
        assertPreviousRound(body, "chat-2");
        assertFalse(body.get("isStream").getAsBoolean());
    }

    @Test
    void shouldNotReuseHistoryWithoutSessionId() throws InterruptedException {
        assertFalse(send(request(null, true)).has("dataList"));
        assertFalse(send(request(null, true)).has("dataList"));
    }

    @Test
    void shouldNotReuseAnotherSessionsHistory() throws InterruptedException {
        send(request(sessionId, true));
        send(request(sessionId, true));
        assertFalse(send(request(sessionId + "-new", true)).has("dataList"));
    }

    private ChatCompletionRequest request(String session, boolean stream) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setSessionId(session);
        request.setStream(stream);
        request.setMessages(Collections.singletonList(message("user", "查一下北京到上海的航班。")));
        return request;
    }

    private ChatMessage message(String role, String content) {
        return ChatMessage.builder().role(role).content(content).build();
    }

    private JsonObject send(ChatCompletionRequest request) throws InterruptedException {
        if (request.getStream()) {
            assertFalse(agent.streamCommunicate(request).toList().blockingGet().isEmpty());
        } else {
            assertNotNull(agent.communicate(request));
        }
        JsonObject body = requests.poll(1, TimeUnit.SECONDS);
        assertNotNull(body, "The mock platform should receive the request");
        return body;
    }

    private void assertPreviousRound(JsonObject body, String chatId) {
        JsonArray dataList = body.getAsJsonArray("dataList");
        assertNotNull(dataList);
        assertEquals(1, dataList.size(), "Only the immediately preceding round may be sent");
        JsonObject item = dataList.get(0).getAsJsonObject();
        assertEquals(conversationId, item.get("conversationId").getAsString());
        assertEquals(chatId, item.get("chatId").getAsString());
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            ByteArrayOutputStream input = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = exchange.getRequestBody().read(buffer)) != -1) {
                input.write(buffer, 0, count);
            }
            requests.add(JsonParser.parseString(new String(input.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject());
            JsonObject response = new JsonObject();
            response.addProperty("msgType", "AGENT_CHAT_MESSAGE");
            response.addProperty("chatStatus", "CHAT_STATUS_END");
            response.addProperty("isEnd", true);
            response.addProperty("conversationId", conversationId);
            response.addProperty("chatId", "chat-" + chatNumber.incrementAndGet());
            JsonObject content = new JsonObject();
            content.addProperty("docAskContent", "[{\"sentence\":\"test answer\",\"type\":\"text\"}]");
            response.add("content", content);
            byte[] output = ("data: " + new Gson().toJson(response) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
        } finally {
            exchange.close();
        }
    }
}
