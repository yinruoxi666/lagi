package ai.vector.diagnostics;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Collects one safe, single-line network breakdown for each Chroma HTTP call. */
public final class ChromaHttpPerformanceEventListener extends EventListener {
    private static final AtomicLong NEXT_CALL_ID = new AtomicLong();

    private final VectorSearchPerformanceContext.Snapshot snapshot;
    private final long callId = NEXT_CALL_ID.incrementAndGet();
    private final AtomicBoolean completed = new AtomicBoolean();

    private long callStartedNanos;
    private long proxyStartedNanos;
    private long proxyMs = -1L;
    private long dnsStartedNanos;
    private long dnsMs = -1L;
    private long connectStartedNanos;
    private long connectMs = -1L;
    private long tlsStartedNanos;
    private long tlsMs = -1L;
    private long requestStartedNanos;
    private long requestEndedNanos;
    private long requestMs = -1L;
    private long responseHeadersStartedNanos;
    private long responseBodyStartedNanos;
    private long responseReadMs = -1L;
    private long requestBytes = -1L;
    private long responseBytes = -1L;
    private boolean connectStarted;
    private boolean connectionReused;
    private String selectedProxies = "[]";
    private String dnsAddresses = "[]";
    private String remoteAddress = "unknown";
    private String routeProxy = "unknown";
    private String protocol = "unknown";
    private String connectionHeader = "unknown";
    private int statusCode = -1;

    public ChromaHttpPerformanceEventListener(VectorSearchPerformanceContext.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void callStart(Call call) {
        callStartedNanos = System.nanoTime();
        snapshot.info("Chroma HTTP开始：callId={}，operation={}，method={}，url={}",
                callId, operation(call), call.request().method(), safeUrl(call.request().url()));
    }

    @Override
    public void proxySelectStart(Call call, HttpUrl url) {
        proxyStartedNanos = System.nanoTime();
    }

    @Override
    public void proxySelectEnd(Call call, HttpUrl url, List<Proxy> proxies) {
        proxyMs = elapsed(proxyStartedNanos);
        selectedProxies = proxies == null ? "[]" : proxies.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        dnsStartedNanos = System.nanoTime();
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        dnsMs = elapsed(dnsStartedNanos);
        dnsAddresses = inetAddressList == null ? "[]" : inetAddressList.stream()
                .map(InetAddress::getHostAddress)
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
        connectStarted = true;
        connectStartedNanos = System.nanoTime();
        remoteAddress = String.valueOf(inetSocketAddress);
        routeProxy = String.valueOf(proxy);
    }

    @Override
    public void secureConnectStart(Call call) {
        tlsStartedNanos = System.nanoTime();
    }

    @Override
    public void secureConnectEnd(Call call, Handshake handshake) {
        tlsMs = elapsed(tlsStartedNanos);
    }

    @Override
    public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
        connectMs = elapsed(connectStartedNanos);
        remoteAddress = String.valueOf(inetSocketAddress);
        routeProxy = String.valueOf(proxy);
        this.protocol = String.valueOf(protocol);
    }

    @Override
    public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy,
                              Protocol protocol, IOException ioe) {
        connectMs = elapsed(connectStartedNanos);
        remoteAddress = String.valueOf(inetSocketAddress);
        routeProxy = String.valueOf(proxy);
        this.protocol = String.valueOf(protocol);
    }

    @Override
    public void connectionAcquired(Call call, Connection connection) {
        connectionReused = !connectStarted;
        if (connection != null) {
            remoteAddress = String.valueOf(connection.socket().getRemoteSocketAddress());
            routeProxy = String.valueOf(connection.route().proxy());
            protocol = String.valueOf(connection.protocol());
        }
    }

    @Override
    public void requestHeadersStart(Call call) {
        requestStartedNanos = System.nanoTime();
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
        requestEndedNanos = System.nanoTime();
        requestMs = nanosToMillis(requestEndedNanos - requestStartedNanos);
    }

    @Override
    public void requestBodyEnd(Call call, long byteCount) {
        requestBytes = byteCount;
        requestEndedNanos = System.nanoTime();
        requestMs = nanosToMillis(requestEndedNanos - requestStartedNanos);
    }

    @Override
    public void responseHeadersStart(Call call) {
        responseHeadersStartedNanos = System.nanoTime();
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        statusCode = response.code();
        connectionHeader = String.valueOf(response.header("Connection"));
        if (response.protocol() != null) {
            protocol = String.valueOf(response.protocol());
        }
    }

    @Override
    public void responseBodyStart(Call call) {
        responseBodyStartedNanos = System.nanoTime();
    }

    @Override
    public void responseBodyEnd(Call call, long byteCount) {
        responseBytes = byteCount;
        responseReadMs = elapsed(responseBodyStartedNanos);
    }

    @Override
    public void callEnd(Call call) {
        complete(call, "success", null);
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
        complete(call, "error", ioe);
    }

    private void complete(Call call, String status, IOException failure) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        long totalMs = elapsed(callStartedNanos);
        long ttfbMs = responseHeadersStartedNanos == 0L || requestEndedNanos == 0L
                ? -1L : nanosToMillis(responseHeadersStartedNanos - requestEndedNanos);
        String failureText = failure == null
                ? "none"
                : failure.getClass().getSimpleName() + ":" + singleLine(failure.getMessage());
        String operation = operation(call);
        snapshot.recordMetric("chroma.http." + operation, totalMs);
        snapshot.info(
                "Chroma HTTP结束：callId={}，operation={}，method={}，url={}，状态={}，statusCode={}，总耗时={}ms，代理选择={}ms，DNS={}ms，连接={}ms，TLS={}ms，请求发送={}ms，首字节={}ms，响应读取={}ms，请求字节={}，响应字节={}，连接复用={}，代理={}，候选代理={}，DNS地址={}，远端={}，协议={}，Connection头={}，异常={}",
                callId, operation, call.request().method(), safeUrl(call.request().url()), status, statusCode,
                totalMs, proxyMs, dnsMs, connectMs, tlsMs, requestMs, ttfbMs, responseReadMs,
                requestBytes, responseBytes, connectionReused, routeProxy, selectedProxies, dnsAddresses,
                remoteAddress, protocol, connectionHeader, failureText);
        snapshot.warnIfSlow("Chroma HTTP " + operation, totalMs,
                VectorSearchPerformanceContext.HTTP_WARN_MS);
    }

    private String operation(Call call) {
        String path = call.request().url().encodedPath();
        if (path.endsWith("/query")) {
            return "query";
        }
        if (path.endsWith("/get")) {
            return "get";
        }
        if (path.endsWith("/count")) {
            return "count";
        }
        if (path.contains("/collections")) {
            return "collection";
        }
        if (path.contains("/databases")) {
            return "database";
        }
        if (path.contains("/tenants")) {
            return "tenant";
        }
        return "other";
    }

    private String safeUrl(HttpUrl url) {
        return url.scheme() + "://" + url.host() + ":" + url.port() + url.encodedPath();
    }

    private long elapsed(long startedNanos) {
        if (startedNanos == 0L) {
            return -1L;
        }
        return nanosToMillis(System.nanoTime() - startedNanos);
    }

    private long nanosToMillis(long nanos) {
        return nanos < 0L ? -1L : nanos / 1_000_000L;
    }

    private String singleLine(String value) {
        return value == null ? "null" : VectorSearchPerformanceContext.escapeForSingleLine(value);
    }
}
