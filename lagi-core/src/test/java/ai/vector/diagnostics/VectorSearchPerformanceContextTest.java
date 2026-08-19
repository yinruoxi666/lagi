package ai.vector.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorSearchPerformanceContextTest {
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
    void resolvesAndSanitizesRequestId() {
        assertEquals("trace-123:_test", VectorSearchPerformanceContext.resolveRequestId("trace-123:_test"));
        assertNotEquals("bad request\nvalue",
                VectorSearchPerformanceContext.resolveRequestId("bad request\nvalue"));
        assertEquals("line1\\nline2\\tend",
                VectorSearchPerformanceContext.escapeForSingleLine("line1\nline2\tend"));
    }

    @Test
    void propagatesContextToTaskAndClearsWorkerAfterCompletion() throws Exception {
        ThreadPoolExecutor executor = executor("context-test", new ThreadPoolExecutor.AbortPolicy());
        try {
            try (VectorSearchPerformanceContext.Scope scope = VectorSearchPerformanceContext.open("request-123")) {
                VectorSearchPerformanceContext.TimedFuture<String> future =
                        VectorSearchPerformanceContext.submit(executor, "context-test", "lookup", "one",
                                VectorSearchPerformanceContext::currentRequestId);
                assertEquals("request-123", future.get());
                scope.finish("success", 1, 10L);
            }

            assertFalse(executor.submit(VectorSearchPerformanceContext::isActive).get());
            assertTrue(messagesContain("任务开始"));
            assertTrue(messagesContain("request-123"));
            assertTrue(messagesContain("线程启动"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void logsWarnWhenStageExceedsThreshold() {
        try (VectorSearchPerformanceContext.Scope ignored = VectorSearchPerformanceContext.open("slow-request")) {
            VectorSearchPerformanceContext.recordStage("test.http", "测试HTTP", 501L,
                    VectorSearchPerformanceContext.HTTP_WARN_MS);
        }

        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("测试HTTP")
                        && event.getFormattedMessage().contains("501ms")));
    }

    @Test
    void detectsCallerRunsWhenPoolAndQueueAreFull() throws Exception {
        ThreadPoolExecutor executor = executor("caller-runs-test", new ThreadPoolExecutor.CallerRunsPolicy());
        CountDownLatch blockingTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockingTask = new CountDownLatch(1);
        try {
            try (VectorSearchPerformanceContext.Scope ignored = VectorSearchPerformanceContext.open("caller-runs")) {
                VectorSearchPerformanceContext.TimedFuture<String> first =
                        VectorSearchPerformanceContext.submit(executor, "caller-runs-test", "blocking", "one", () -> {
                            blockingTaskStarted.countDown();
                            releaseBlockingTask.await(5, TimeUnit.SECONDS);
                            return "one";
                        });
                assertTrue(blockingTaskStarted.await(5, TimeUnit.SECONDS));
                VectorSearchPerformanceContext.TimedFuture<String> second =
                        VectorSearchPerformanceContext.submit(executor, "caller-runs-test", "queued", "two", () -> "two");
                VectorSearchPerformanceContext.TimedFuture<String> third =
                        VectorSearchPerformanceContext.submit(executor, "caller-runs-test", "inline", "three", () -> "three");

                assertEquals("three", third.get());
                Thread.sleep(150L);
                releaseBlockingTask.countDown();
                assertEquals("one", first.get());
                assertEquals("two", second.get());
            }

            assertTrue(messagesContain("CallerRuns=true"));
            assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("排队")
                            && event.getFormattedMessage().contains("阈值=100ms")));
        } finally {
            releaseBlockingTask.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void logsWarnForSlowTaskExecutionAndFutureWait() throws Exception {
        ThreadPoolExecutor executor = executor("slow-task-test", new ThreadPoolExecutor.AbortPolicy());
        try {
            try (VectorSearchPerformanceContext.Scope ignored = VectorSearchPerformanceContext.open("slow-task")) {
                VectorSearchPerformanceContext.TimedFuture<String> future =
                        VectorSearchPerformanceContext.submit(executor, "slow-task-test", "slow", "one", () -> {
                            Thread.sleep(1100L);
                            return "done";
                        });
                assertEquals("done", future.get());
            }

            assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("任务one执行")
                            && event.getFormattedMessage().contains("阈值=1000ms")));
            assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("Future等待")
                            && event.getFormattedMessage().contains("阈值=1000ms")));
        } finally {
            executor.shutdownNow();
        }
    }

    private ThreadPoolExecutor executor(String poolName, RejectedExecutionHandler handler) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                VectorSearchPerformanceContext.diagnosticThreadFactory(poolName),
                handler
        );
    }

    private boolean messagesContain(String expected) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(expected));
    }
}
