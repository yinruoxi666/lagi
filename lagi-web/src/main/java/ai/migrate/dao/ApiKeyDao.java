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
                    "api_address TEXT," +
                    "status INTEGER NOT NULL DEFAULT 0," +
                    "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.executeUpdate(sql);
        }
        initialized = true;
    }

    public List<ModelApiKey> listAll() throws SQLException {
        ensureTable();
        List<ModelApiKey> list = new ArrayList<ModelApiKey>();
        String sql = "SELECT id,name,provider,api_key,api_address,status,created_time FROM api_key_store ORDER BY id DESC";
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

    public ModelApiKey findById(Long id) throws SQLException {
        if (id == null) {
            return null;
        }
        ensureTable();
        String sql = "SELECT id,name,provider,api_key,api_address,status,created_time FROM api_key_store WHERE id = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
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
        String sql = "INSERT INTO api_key_store(name,provider,api_key,api_address,status) VALUES(?,?,?,?,?)";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getProvider());
            ps.setString(3, item.getApiKey());
            ps.setString(4, item.getApiAddress());
            ps.setInt(5, item.getStatus() == null ? 0 : item.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    public int deleteById(Long id) throws SQLException {
        if (id == null) {
            return 0;
        }
        ensureTable();
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement("DELETE FROM api_key_store WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    public void setStatusById(Long id, int status) throws SQLException {
        ensureTable();
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement("UPDATE api_key_store SET status = ? WHERE id = ?")) {
            ps.setInt(1, status);
            ps.setLong(2, id);
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
        item.setApiAddress(rs.getString("api_address"));
        item.setStatus(rs.getInt("status"));
        item.setCreatedTime(rs.getTimestamp("created_time"));
        return item;
    }
}
