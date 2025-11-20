package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.database.impl.SqliteAdapter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FilterConfigService {
    private static final SqliteAdapter sqliteAdapter = new SqliteAdapter();
    public static final Map<String, FilterConfig> filterConfigCache = new ConcurrentHashMap<>();
    private static volatile boolean tableInitialized = false;

    private static synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try {
            Connection conn = sqliteAdapter.getCon();
            String sql = "CREATE TABLE IF NOT EXISTS lagi_filter_config (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name VARCHAR(64) NOT NULL UNIQUE," +
                "rules TEXT," +
                "groups TEXT," +
                "filter_window_length INTEGER DEFAULT 0," +
                "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")";
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
            conn.close();
            tableInitialized = true;
            loadFromDatabase();
        } catch (Exception e) {
            log.error("初始化 lagi_filter_config 表失败", e);
        }
    }

    public static void loadFromDatabase() {
        try {
            ensureTableExists();
            Connection conn = sqliteAdapter.getCon();
            String sql = "SELECT * FROM lagi_filter_config";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            
            filterConfigCache.clear();
            while (rs.next()) {
                FilterConfig config = new FilterConfig();
                config.setName(rs.getString("name"));
                config.setRules(rs.getString("rules"));
                
                String groupsJson = rs.getString("groups");
                if (groupsJson != null && !groupsJson.trim().isEmpty()) {
                    try {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        List<Map<String, String>> groupsList = gson.fromJson(groupsJson, 
                            new com.google.gson.reflect.TypeToken<List<Map<String, String>>>(){}.getType());
                        List<FilterRule> filterRules = new ArrayList<>();
                        for (Map<String, String> groupMap : groupsList) {
                            FilterRule rule = new FilterRule();
                            rule.setLevel(groupMap.get("level"));
                            rule.setRules(groupMap.get("rules"));
                            rule.setMask(groupMap.get("mask"));
                            filterRules.add(rule);
                        }
                        config.setGroups(filterRules);
                    } catch (Exception e) {
                        log.warn("解析 groups JSON 失败: {}", groupsJson, e);
                    }
                }
                
                config.setFilterWindowLength(rs.getInt("filter_window_length"));
                filterConfigCache.put(config.getName(), config);
            }
            rs.close();
            conn.close();
        } catch (Exception e) {
            log.error("从数据库加载过滤器配置失败", e);
        }
    }

    public static List<FilterConfig> list() {
        ensureTableExists();
        return new ArrayList<>(filterConfigCache.values());
    }

    public static void add(FilterConfig config) {
        ensureTableExists();
        
        // 如果过滤器已存在，自动转为更新操作（upsert模式）
        if (filterConfigCache.containsKey(config.getName())) {
            log.info("过滤器 {} 已存在，自动转为更新操作", config.getName());
            update(config);
            return;
        }
        
        try {
            Connection conn = sqliteAdapter.getCon();
            String sql = "INSERT INTO lagi_filter_config (name, rules, groups, filter_window_length) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, config.getName());
            pstmt.setString(2, config.getRules());
            
            String groupsJson = null;
            if (config.getGroups() != null && !config.getGroups().isEmpty()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                List<Map<String, String>> groupsList = new ArrayList<>();
                for (FilterRule rule : config.getGroups()) {
                    Map<String, String> groupMap = new HashMap<>();
                    groupMap.put("level", rule.getLevel());
                    groupMap.put("rules", rule.getRules());
                    groupMap.put("mask", rule.getMask());
                    groupsList.add(groupMap);
                }
                groupsJson = gson.toJson(groupsList);
            }
            pstmt.setString(3, groupsJson);
            pstmt.setInt(4, config.getFilterWindowLength());
            
            pstmt.executeUpdate();
            pstmt.close();
            conn.close();
            
            filterConfigCache.put(config.getName(), config);
            log.info("添加过滤器配置成功: {}", config.getName());
        } catch (Exception e) {
            log.error("添加过滤器配置失败", e);
            throw new RuntimeException("添加失败: " + e.getMessage(), e);
        }
    }

    public static void update(FilterConfig config) {
        ensureTableExists();
        if (!filterConfigCache.containsKey(config.getName())) {
            throw new RuntimeException("找不到要更新的过滤器: " + config.getName());
        }
        
        try {
            Connection conn = sqliteAdapter.getCon();
            String sql = "UPDATE lagi_filter_config SET rules = ?, groups = ?, filter_window_length = ?, update_time = datetime('now') WHERE name = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, config.getRules());
            
            String groupsJson = null;
            if (config.getGroups() != null && !config.getGroups().isEmpty()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                List<Map<String, String>> groupsList = new ArrayList<>();
                for (FilterRule rule : config.getGroups()) {
                    Map<String, String> groupMap = new HashMap<>();
                    groupMap.put("level", rule.getLevel());
                    groupMap.put("rules", rule.getRules());
                    groupMap.put("mask", rule.getMask());
                    groupsList.add(groupMap);
                }
                groupsJson = gson.toJson(groupsList);
            }
            pstmt.setString(2, groupsJson);
            pstmt.setInt(3, config.getFilterWindowLength());
            pstmt.setString(4, config.getName());
            
            pstmt.executeUpdate();
            pstmt.close();
            conn.close();
            
            filterConfigCache.put(config.getName(), config);
            log.info("更新过滤器配置成功: {}", config.getName());
        } catch (Exception e) {
            log.error("更新过滤器配置失败", e);
            throw new RuntimeException("更新失败: " + e.getMessage(), e);
        }
    }

    public static void delete(String name) {
        ensureTableExists();
        if (!filterConfigCache.containsKey(name)) {
            throw new RuntimeException("找不到要删除的过滤器: " + name);
        }
        
        try {
            Connection conn = sqliteAdapter.getCon();
            String sql = "DELETE FROM lagi_filter_config WHERE name = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            pstmt.close();
            conn.close();
            
            filterConfigCache.remove(name);
            log.info("删除过滤器配置成功: {}", name);
        } catch (Exception e) {
            log.error("删除过滤器配置失败", e);
            throw new RuntimeException("删除失败: " + e.getMessage(), e);
        }
    }
}

