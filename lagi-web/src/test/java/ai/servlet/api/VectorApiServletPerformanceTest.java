package ai.servlet.api;

import ai.common.pojo.IndexSearchData;
import ai.openai.pojo.ChatCompletionRequest;
import ai.vector.diagnostics.VectorSearchPerformanceContext;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorApiServletPerformanceTest {
    private VectorApiServlet.MetadataSearchExecutor searchExecutor;
    private VectorApiServlet servlet;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        searchExecutor = mock(VectorApiServlet.MetadataSearchExecutor.class);
        servlet = new VectorApiServlet(searchExecutor);
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
    void textBranchReusesRequestIdWritesHeaderAndLogsEveryEndpointStage() throws Exception {
        String body = "{\n\"category\":\"cat\",\"text\":\"hello\",\"where\":{\"knowledge_id\":\"one\"}}";
        IndexSearchData hit = new IndexSearchData();
        hit.setId("hit-1");
        hit.setText("answer");
        when(searchExecutor.searchText(eq("hello"), anyMap(), eq("cat")))
                .thenReturn(Collections.singletonList(hit));
        RequestResponse exchange = exchange(body, "client-request-123");

        servlet.doPost(exchange.request, exchange.response);

        verify(exchange.response).setHeader(VectorSearchPerformanceContext.REQUEST_ID_HEADER,
                "client-request-123");
        assertTrue(exchange.responseBody.toString().contains("\"status\":\"success\""));
        assertTrue(exchange.responseBody.toString().contains("\"id\":\"hit-1\""));
        String messages = allMessages();
        assertTrue(messages.contains("请求ID=client-request-123"));
        assertTrue(messages.contains("完整请求体={\\n"));
        assertTrue(messages.contains("进入text检索分支"));
        assertTrue(messages.contains("请求体读取完成"));
        assertTrue(messages.contains("请求体反序列化完成"));
        assertTrue(messages.contains("响应序列化完成"));
        assertTrue(messages.contains("响应写出完成"));
        assertTrue(messages.contains("请求结束：状态=success，结果数=1"));
        assertFalse(VectorSearchPerformanceContext.isActive());
    }

    @Test
    void messagesBranchGeneratesRequestIdAndReturnsUnchangedFailedJsonForEmptyResult() throws Exception {
        String body = "{\"category\":\"cat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"where\":{}}";
        when(searchExecutor.searchMessages(any(ChatCompletionRequest.class), anyMap()))
                .thenReturn(Collections.emptyList());
        RequestResponse exchange = exchange(body, "invalid request id");

        servlet.doPost(exchange.request, exchange.response);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(exchange.response).setHeader(eq(VectorSearchPerformanceContext.REQUEST_ID_HEADER),
                requestId.capture());
        assertNotEquals("invalid request id", requestId.getValue());
        assertTrue(exchange.responseBody.toString().contains("\"status\":\"failed\""));
        assertTrue(allMessages().contains("进入messages检索分支"));
        assertTrue(allMessages().contains("请求结束：状态=failed，结果数=0"));
        assertFalse(VectorSearchPerformanceContext.isActive());
    }

    @Test
    void exceptionIsLoggedSummarizedAndClearsRequestContext() throws Exception {
        String body = "{\"category\":\"cat\",\"text\":\"hello\",\"where\":{}}";
        when(searchExecutor.searchText(anyString(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("external failure"));
        RequestResponse exchange = exchange(body, "request-error");

        assertThrows(IllegalStateException.class,
                () -> servlet.doPost(exchange.request, exchange.response));

        String messages = allMessages();
        assertTrue(messages.contains("searchByMetadata请求处理异常"));
        assertTrue(messages.contains("请求结束：状态=error，结果数=-1"));
        assertFalse(VectorSearchPerformanceContext.isActive());
    }

    private RequestResponse exchange(String body, String requestId) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/vector/searchByMetadata");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(VectorSearchPerformanceContext.REQUEST_ID_HEADER)).thenReturn(requestId);
        when(request.getContentLength()).thenReturn(bytes.length);
        when(request.getInputStream()).thenReturn(new ByteArrayServletInputStream(bytes));

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        return new RequestResponse(request, response, responseBody);
    }

    private String allMessages() {
        StringBuilder messages = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            messages.append(event.getFormattedMessage()).append('\n');
        }
        return messages.toString();
    }

    private static final class RequestResponse {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final StringWriter responseBody;

        private RequestResponse(HttpServletRequest request, HttpServletResponse response,
                                StringWriter responseBody) {
            this.request = request;
            this.response = response;
            this.responseBody = responseBody;
        }
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private ByteArrayServletInputStream(byte[] content) {
            this.delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
        }
    }
}
