package ai.migrate.dao;

import ai.common.db.HikariDS;
import ai.dto.ModelApiKey;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ApiKeyDao {
    private static volatile boolean initialized = false;

    private static synchronized void ensureTable() throws SQLException {
        if (initialized) {
            return;
        }
        try (Connection conn = HikariDS.getConnection("saas");
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS api_key_store (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "provider TEXT NOT NULL," +
                    "api_key TEXT NOT NULL," +
                    "user_id TEXT," +
                    "api_address TEXT," +
                    "status INTEGER NOT NULL DEFAULT 0," +
                    "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.executeUpdate(sql);
            try {
                stmt.executeUpdate("ALTER TABLE api_key_store ADD COLUMN user_id TEXT");
            } catch (SQLException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (!msg.contains("duplicate column name")) {
                    throw e;
                }
            }
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_api_key_store_api_key ON api_key_store(api_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_api_key_store_user_id ON api_key_store(user_id)");
        }
        initialized = true;
    }

    public List<ModelApiKey> listAll() throws SQLException {
        ensureTable();
        List<ModelApiKey> list = new ArrayList<ModelApiKey>();
        String sql = "SELECT id,name,provider,api_key,user_id,api_address,status,created_time FROM api_key_store ORDER BY id DESC";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<ModelApiKey> listByUserIdOrEmpty(String userId) throws SQLException {
        ensureTable();
        List<ModelApiKey> list = new ArrayList<ModelApiKey>();
        String normalizedUserId = userId == null ? "" : userId.trim();
        String sql;
        if (normalizedUserId.isEmpty()) {
            sql = "SELECT id,name,provider,api_key,user_id,api_address,status,created_time " +
                    "FROM api_key_store WHERE user_id IS NULL OR user_id = '' ORDER BY id DESC";
            try (Connection conn = HikariDS.getConnection("saas");
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } else {
            sql = "SELECT id,name,provider,api_key,user_id,api_address,status,created_time " +
                    "FROM api_key_store WHERE user_id = ? OR user_id IS NULL OR user_id = '' ORDER BY id DESC";
            try (Connection conn = HikariDS.getConnection("saas");
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normalizedUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
        }
        return list;
    }

    public ModelApiKey findByApiKey(String apiKey) throws SQLException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        ensureTable();
        String sql = "SELECT id,name,provider,api_key,user_id,api_address,status,created_time FROM api_key_store WHERE api_key = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public long insert(ModelApiKey item) throws SQLException {
        ensureTable();
        String sql = "INSERT INTO api_key_store(name,provider,api_key,user_id,api_address,status) VALUES(?,?,?,?,?,?)";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getProvider());
            ps.setString(3, item.getApiKey());
            ps.setString(4, item.getUserId());
            ps.setString(5, item.getApiAddress());
            ps.setInt(6, item.getStatus() == null ? 0 : item.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    public boolean existsByApiKey(String apiKey) throws SQLException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        ensureTable();
        String sql = "SELECT 1 FROM api_key_store WHERE api_key = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int deleteByApiKey(String apiKey) throws SQLException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return 0;
        }
        ensureTable();
        String sql = "DELETE FROM api_key_store WHERE api_key = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey.trim());
            return ps.executeUpdate();
        }
    }

    public void setStatusByApiKey(String apiKey, int status) throws SQLException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }
        ensureTable();
        String sql = "UPDATE api_key_store SET status = ? WHERE api_key = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, status);
            ps.setString(2, apiKey.trim());
            ps.executeUpdate();
        }
    }

    public void disableProviderKeys(String provider) throws SQLException {
        ensureTable();
        String sql = "UPDATE api_key_store SET status = 0 WHERE provider = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, provider);
            ps.executeUpdate();
        }
    }

    private ModelApiKey mapRow(ResultSet rs) throws SQLException {
        ModelApiKey item = new ModelApiKey();
        item.setId(rs.getLong("id"));
        item.setName(rs.getString("name"));
        item.setProvider(rs.getString("provider"));
        item.setApiKey(rs.getString("api_key"));
        item.setUserId(rs.getString("user_id"));
        item.setApiAddress(rs.getString("api_address"));
        item.setStatus(rs.getInt("status"));
        item.setCreatedTime(rs.getTimestamp("created_time"));
        return item;
    }
}
