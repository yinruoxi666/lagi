package ai.servlet;

import ai.database.impl.SqliteAdapter;
import ai.servlet.annotation.Get;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class FenceServlet extends RestfulServlet {
    private static final long serialVersionUID = 1L;
    private SqliteAdapter sqliteAdapter = new SqliteAdapter();
    private static boolean tableInitialized = false;

    private synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try {
            Connection conn = sqliteAdapter.getCon();
            DatabaseMetaData dbm = conn.getMetaData();
            ResultSet tables = dbm.getTables(null, null, "lagi_filter_monitor", null);
            if (!tables.next()) {
                Statement stmt = conn.createStatement();
                String createTable = "CREATE TABLE IF NOT EXISTS lagi_filter_monitor (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "filter_name VARCHAR(64) NOT NULL," +
                    "action_type VARCHAR(32) NOT NULL," +
                    "content TEXT," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";
                stmt.executeUpdate(createTable);
                stmt.close();
            }
            conn.close();
            tableInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Get("list")
    public List<Map<String, Object>> list() {
        ensureTableExists();
        String sql = "SELECT * FROM lagi_filter_monitor ORDER BY create_time DESC LIMIT 1000";
        return sqliteAdapter.select(sql);
    }

    @Get("stats")
    public Map<String, Object> stats() {
        ensureTableExists();
        String totalSql = "SELECT COUNT(*) as cnt FROM lagi_filter_monitor";
        String todaySql = "SELECT COUNT(*) as cnt FROM lagi_filter_monitor WHERE DATE(create_time) = DATE('now', 'localtime')";
        String hourSql = "SELECT COUNT(*) as cnt FROM lagi_filter_monitor WHERE create_time >= datetime('now', 'localtime', '-1 hour')";
        
        List<Map<String, Object>> totalResult = sqliteAdapter.select(totalSql);
        List<Map<String, Object>> todayResult = sqliteAdapter.select(todaySql);
        List<Map<String, Object>> hourResult = sqliteAdapter.select(hourSql);
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", totalResult.isEmpty() ? 0 : totalResult.get(0).get("cnt"));
        stats.put("today", todayResult.isEmpty() ? 0 : todayResult.get(0).get("cnt"));
        stats.put("hour", hourResult.isEmpty() ? 0 : hourResult.get(0).get("cnt"));
        
        return stats;
    }
}

