package ai.utils;

import ai.database.impl.SqliteAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class FilterMonitorUtil {
    private static final SqliteAdapter sqliteAdapter = new SqliteAdapter();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static volatile boolean tableInitialized = false;
    private static final int MAX_RETRY = 3;

    private static synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try {
            Connection conn = sqliteAdapter.getCon();
            DatabaseMetaData dbm = conn.getMetaData();
            ResultSet tables = dbm.getTables(null, null, "lagi_filter_monitor", null);
            if (!tables.next()) {
                String createTable = "CREATE TABLE IF NOT EXISTS lagi_filter_monitor (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "filter_name VARCHAR(64) NOT NULL," +
                    "action_type VARCHAR(32) NOT NULL," +
                    "content TEXT," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";
                conn.createStatement().executeUpdate(createTable);
            }
            conn.close();
            tableInitialized = true;
        } catch (Exception e) {
            log.error("初始化 lagi_filter_monitor 表失败", e);
        }
    }

    public static void recordFilterAction(String filterName, String actionType, String content) {
        // 跳过系统操作类型的记录，如 reload 等，这些不需要写入监控表
        if ("reload".equalsIgnoreCase(actionType)) {
            log.debug("跳过记录系统操作: filterName={}, actionType={}", filterName, actionType);
            return;
        }
        
        executorService.submit(() -> {
            try {
                ensureTableExists();
                int retry = 0;
                while (retry <= MAX_RETRY) {
                    try (Connection conn = sqliteAdapter.getCon()) {
                        if (conn == null || conn.isClosed()) {
                            log.warn("数据库连接不可用，跳过记录过滤操作");
                            return;
                        }

                        String sql = "INSERT INTO lagi_filter_monitor (filter_name, action_type, content, create_time) VALUES (?, ?, ?, datetime('now', 'localtime'))";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, filterName != null ? filterName : "");
                            pstmt.setString(2, actionType != null ? actionType : "");

                            String contentToSave = content;
                            if (contentToSave != null) {
                                if (contentToSave.length() > 1000) {
                                    contentToSave = contentToSave.substring(0, 1000);
                                }
                                try {
                                    byte[] bytes = contentToSave.getBytes(StandardCharsets.UTF_8);
                                    String utf8Content = new String(bytes, StandardCharsets.UTF_8);
                                    pstmt.setString(3, utf8Content);
                                } catch (Exception e) {
                                    pstmt.setString(3, contentToSave.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", ""));
                                }
                            } else {
                                pstmt.setString(3, null);
                            }

                            pstmt.executeUpdate();
                            log.debug("记录过滤操作: filterName={}, actionType={}", filterName, actionType);
                            return;
                        }
                    } catch (SQLException e) {
                        if (!isSqliteBusy(e) || retry == MAX_RETRY) {
                            throw e;
                        }
                        retry++;
                        sleepQuietly(100L * retry);
                    }
                }
            } catch (Exception e) {
                log.error("记录过滤操作失败: filterName={}, actionType={}", filterName, actionType, e);
            }
        });
    }

    private static boolean isSqliteBusy(SQLException e) {
        return e.getMessage() != null && e.getMessage().contains("SQLITE_BUSY");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
