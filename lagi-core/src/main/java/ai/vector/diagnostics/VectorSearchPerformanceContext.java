package ai.vector.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Temporary request-scoped diagnostics for /v1/vector/searchByMetadata.
 */
public final class VectorSearchPerformanceContext {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final long HTTP_WARN_MS = 500L;
    public static final long EMBEDDING_WARN_MS = 1000L;
    public static final long TASK_QUEUE_WARN_MS = 100L;
    public static final long TASK_EXECUTION_WARN_MS = 1000L;
    public static final long FUTURE_WAIT_WARN_MS = 1000L;
    public static final long ENDPOINT_WARN_MS = 3000L;

    private static final String PREFIX = "[VECTOR_SEARCH_PERF]";
    private static final Logger LOG = LoggerFactory.getLogger(VectorSearchPerformanceContext.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private VectorSearchPerformanceContext() {
    }

    public static Scope open(String requestedRequestId) {
        Trace previous = CURRENT.get();
        Trace trace = new Trace(resolveRequestId(requestedRequestId));
        CURRENT.set(trace);
        return new Scope(previous, trace);
    }

    public static String resolveRequestId(String requestedRequestId) {
        if (requestedRequestId != null && SAFE_REQUEST_ID.matcher(requestedRequestId).matches()) {
            return requestedRequestId;
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static String currentRequestId() {
        Trace trace = CURRENT.get();
        return trace == null ? null : trace.requestId;
    }

    public static Snapshot capture() {
        Trace trace = CURRENT.get();
        return trace == null ? null : new Snapshot(trace);
    }

    public static long startTimer() {
        return System.nanoTime();
    }

    public static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public static void info(String format, Object... arguments) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        String message = MessageFormatter.arrayFormat(format, arguments).getMessage();
        LOG.info("{} 请求ID={}，线程={}，{}", PREFIX, trace.requestId,
                Thread.currentThread().getName(), message);
    }

    public static void warn(String format, Object... arguments) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        String message = MessageFormatter.arrayFormat(format, arguments).getMessage();
        LOG.warn("{} 请求ID={}，线程={}，{}", PREFIX, trace.requestId,
                Thread.currentThread().getName(), message);
    }

    public static void error(String message, Throwable throwable) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        LOG.error("{} 请求ID={}，线程={}，{}", PREFIX, trace.requestId,
                Thread.currentThread().getName(), message, throwable);
    }

    public static void recordStage(String metricName, String description, long elapsedMs, long warnThresholdMs) {
        if (!isActive()) {
            return;
        }
        recordMetric(metricName, elapsedMs);
        info("{}完成，耗时={}ms", description, elapsedMs);
        warnIfSlow(description, elapsedMs, warnThresholdMs);
    }

    public static void recordMetric(String metricName, long elapsedMs) {
        Trace trace = CURRENT.get();
        if (trace != null) {
            trace.record(metricName, elapsedMs);
        }
    }

    public static void increment(String metricName) {
        recordMetric(metricName, 0L);
    }

    public static void recordCache(String cacheName, boolean hit, long elapsedMs) {
        if (!isActive()) {
            return;
        }
        String state = hit ? "命中" : "未命中";
        recordMetric("cache." + cacheName + "." + (hit ? "hit" : "miss"), elapsedMs);
        info("{}缓存{}，查找耗时={}ms", cacheName, state, elapsedMs);
    }

    public static void warnIfSlow(String description, long elapsedMs, long warnThresholdMs) {
        if (isActive() && warnThresholdMs >= 0L && elapsedMs > warnThresholdMs) {
            warn("慢阶段告警：{}耗时={}ms，阈值={}ms", description, elapsedMs, warnThresholdMs);
        }
    }

    public static String escapeForSingleLine(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (Character.isISOControl(ch)) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
            }
        }
        return escaped.toString();
    }

    public static ThreadFactory diagnosticThreadFactory(String poolName) {
        return runnable -> {
            Trace trace = CURRENT.get();
            long requestedNanos = startTimer();
            String threadName = poolName + "-" + UUID.randomUUID();
            Thread thread = new Thread(() -> {
                long startDelayMs = elapsedMillis(requestedNanos);
                if (trace != null) {
                    runWithTrace(trace, () -> {
                        trace.record("thread." + poolName + ".start", startDelayMs);
                        info("线程启动：线程池={}，线程名={}，从创建请求到运行耗时={}ms",
                                poolName, threadName, startDelayMs);
                    });
                }
                runnable.run();
            }, threadName);
            if (trace != null) {
                runWithTrace(trace, () -> info("线程已创建：线程池={}，线程名={}，构造耗时={}ms",
                        poolName, threadName, elapsedMillis(requestedNanos)));
            }
            return thread;
        };
    }

    public static <T> TimedFuture<T> submit(ThreadPoolExecutor executor, String poolName,
                                             String taskType, String taskId, Callable<T> callable) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return new TimedFuture<>(executor.submit(callable), null, poolName, taskType, taskId);
        }

        long submittedNanos = startTimer();
        long submitterThreadId = Thread.currentThread().getId();
        trace.record("task." + poolName + ".submitted", 0L);
        info("任务提交开始：线程池={}，任务类型={}，任务ID={}，线程池状态={}",
                poolName, taskType, taskId, poolStats(executor));

        Callable<T> wrapped = () -> callWithTrace(trace, () -> {
            long queueMs = elapsedMillis(submittedNanos);
            boolean callerRuns = Thread.currentThread().getId() == submitterThreadId;
            trace.record("task." + poolName + ".queue", queueMs);
            if (callerRuns) {
                trace.record("task." + poolName + ".callerRuns", 0L);
            }
            info("任务开始：线程池={}，任务类型={}，任务ID={}，排队耗时={}ms，CallerRuns={}，线程池状态={}",
                    poolName, taskType, taskId, queueMs, callerRuns, poolStats(executor));
            warnIfSlow("线程池" + poolName + "任务" + taskId + "排队", queueMs, TASK_QUEUE_WARN_MS);

            long executionStartedNanos = startTimer();
            String status = "success";
            try {
                return callable.call();
            } catch (Exception e) {
                status = "error";
                error("任务执行异常：线程池=" + poolName + "，任务类型=" + taskType
                        + "，任务ID=" + taskId, e);
                throw e;
            } finally {
                long executionMs = elapsedMillis(executionStartedNanos);
                trace.record("task." + poolName + ".execution", executionMs);
                info("任务结束：线程池={}，任务类型={}，任务ID={}，状态={}，执行耗时={}ms，线程池状态={}",
                        poolName, taskType, taskId, status, executionMs, poolStats(executor));
                warnIfSlow("线程池" + poolName + "任务" + taskId + "执行", executionMs,
                        TASK_EXECUTION_WARN_MS);
            }
        });

        Future<T> future;
        try {
            future = executor.submit(wrapped);
        } catch (RuntimeException e) {
            error("任务提交异常：线程池=" + poolName + "，任务类型=" + taskType
                    + "，任务ID=" + taskId + "，线程池状态=" + poolStats(executor), e);
            throw e;
        }
        long submitMs = elapsedMillis(submittedNanos);
        trace.record("task." + poolName + ".submitCall", submitMs);
        info("任务提交返回：线程池={}，任务类型={}，任务ID={}，提交调用耗时={}ms，线程池状态={}",
                poolName, taskType, taskId, submitMs, poolStats(executor));
        return new TimedFuture<>(future, trace, poolName, taskType, taskId);
    }

    public static String poolStats(ThreadPoolExecutor executor) {
        return "poolSize=" + executor.getPoolSize()
                + ",active=" + executor.getActiveCount()
                + ",queue=" + executor.getQueue().size()
                + ",completed=" + executor.getCompletedTaskCount();
    }

    private static <T> T callWithTrace(Trace trace, Callable<T> callable) throws Exception {
        Trace previous = CURRENT.get();
        CURRENT.set(trace);
        try {
            return callable.call();
        } finally {
            restore(previous);
        }
    }

    private static void runWithTrace(Trace trace, Runnable runnable) {
        Trace previous = CURRENT.get();
        CURRENT.set(trace);
        try {
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Trace trace) {
        if (trace == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(trace);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Trace previous;
        private final Trace trace;
        private boolean closed;

        private Scope(Trace previous, Trace trace) {
            this.previous = previous;
            this.trace = trace;
        }

        public String getRequestId() {
            return trace.requestId;
        }

        public void finish(String status, int resultCount, long totalMs) {
            trace.record("endpoint.total", totalMs);
            info("请求结束：状态={}，结果数={}，总耗时={}ms，汇总={}",
                    status, resultCount, totalMs, trace.summary());
            warnIfSlow("searchByMetadata接口总耗时", totalMs, ENDPOINT_WARN_MS);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                restore(previous);
            }
        }
    }

    public static final class TimedFuture<T> {
        private final Future<T> future;
        private final Trace trace;
        private final String poolName;
        private final String taskType;
        private final String taskId;

        private TimedFuture(Future<T> future, Trace trace, String poolName, String taskType, String taskId) {
            this.future = future;
            this.trace = trace;
            this.poolName = poolName;
            this.taskType = taskType;
            this.taskId = taskId;
        }

        public T get() throws InterruptedException, ExecutionException {
            if (trace == null) {
                return future.get();
            }
            Trace previous = CURRENT.get();
            CURRENT.set(trace);
            long waitStartedNanos = startTimer();
            info("Future等待开始：线程池={}，任务类型={}，任务ID={}", poolName, taskType, taskId);
            try {
                return future.get();
            } finally {
                long waitMs = elapsedMillis(waitStartedNanos);
                trace.record("future." + poolName + ".wait", waitMs);
                info("Future等待结束：线程池={}，任务类型={}，任务ID={}，等待耗时={}ms",
                        poolName, taskType, taskId, waitMs);
                warnIfSlow("线程池" + poolName + "任务" + taskId + " Future等待", waitMs,
                        FUTURE_WAIT_WARN_MS);
                restore(previous);
            }
        }
    }

    public static final class Snapshot {
        private final Trace trace;

        private Snapshot(Trace trace) {
            this.trace = trace;
        }

        public String getRequestId() {
            return trace.requestId;
        }

        public void info(String format, Object... arguments) {
            runWithTrace(trace, () -> VectorSearchPerformanceContext.info(format, arguments));
        }

        public void warn(String format, Object... arguments) {
            runWithTrace(trace, () -> VectorSearchPerformanceContext.warn(format, arguments));
        }

        public void recordMetric(String metricName, long elapsedMs) {
            trace.record(metricName, elapsedMs);
        }

        public void warnIfSlow(String description, long elapsedMs, long thresholdMs) {
            runWithTrace(trace, () -> VectorSearchPerformanceContext.warnIfSlow(
                    description, elapsedMs, thresholdMs));
        }
    }

    private static final class Trace {
        private final String requestId;
        private final Map<String, Metric> metrics = new ConcurrentHashMap<>();

        private Trace(String requestId) {
            this.requestId = requestId;
        }

        private void record(String name, long elapsedMs) {
            metrics.computeIfAbsent(name, key -> new Metric()).record(elapsedMs);
        }

        private String summary() {
            List<String> names = new ArrayList<>(metrics.keySet());
            Collections.sort(names);
            StringBuilder result = new StringBuilder();
            for (String name : names) {
                if (result.length() > 0) {
                    result.append("；");
                }
                Metric metric = metrics.get(name);
                result.append(name)
                        .append("{次数=").append(metric.count.get())
                        .append(",总耗时=").append(metric.totalMs.get()).append("ms")
                        .append(",最大耗时=").append(metric.maxMs.get()).append("ms}");
            }
            return result.toString();
        }
    }

    private static final class Metric {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();

        private void record(long elapsedMs) {
            count.incrementAndGet();
            totalMs.addAndGet(elapsedMs);
            long previous;
            do {
                previous = maxMs.get();
                if (elapsedMs <= previous) {
                    return;
                }
            } while (!maxMs.compareAndSet(previous, elapsedMs));
        }
    }
}
