package ai.bigdata.impl;

import ai.bigdata.IBigdata;
import ai.bigdata.pojo.TextIndexData;
import ai.common.db.Conn;
import ai.config.pojo.BigdataConfig;
import ai.utils.AiGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite FTS5-based implementation of IBigdata.
 * Uses a single FTS5 virtual table with id, category, and text columns.
 */
public class SqliteSearchAdapter implements IBigdata {
    private static final Logger logger = LoggerFactory.getLogger(SqliteSearchAdapter.class);
    private static final int SEARCH_LIMIT = 1000;
    private static final String FTS_TABLE_NAME = "fts_text_index";

    private final String connName;

    public SqliteSearchAdapter(BigdataConfig config) {
        this.connName = AiGlobal.DEFAULT_DB;
    }

    @Override
    public boolean upsert(TextIndexData data) {
        if (data == null || data.getCategory() == null) {
            return false;
        }
        Conn conn = null;
        PreparedStatement ps = null;
        try {
            conn = new Conn(connName);
            conn.setAutoCommit(false);
            String deleteSql = "DELETE FROM " + FTS_TABLE_NAME + " WHERE id = ? AND category = ?";
            ps = conn.prepareStatement(deleteSql);
            ps.setString(1, data.getId());
            ps.setString(2, data.getCategory());
            ps.executeUpdate();
            String insertSql = "INSERT INTO " + FTS_TABLE_NAME + "(id, category, text) VALUES(?, ?, ?)";
            ps = conn.prepareStatement(insertSql);
            ps.setString(1, data.getId());
            ps.setString(2, data.getCategory());
            ps.setString(3, data.getText() != null ? data.getText() : "");
            ps.executeUpdate();
            conn.commit();
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                logger.error("Error while rolling back transaction: {}", ex.getMessage());
            }
            logger.error("Error while upserting text index data", e);
            return false;
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.error("Error while closing prepared statement or connection: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<TextIndexData> search(String keyword, String category) {
        if (keyword == null || keyword.isEmpty() || category == null) {
            return new ArrayList<>();
        }
        Conn conn = new Conn(connName);
        try {
            String sql = "SELECT id, category, text FROM " + FTS_TABLE_NAME
                    + " WHERE category = ? AND " + FTS_TABLE_NAME + " MATCH ? LIMIT ?";
            List<TextIndexData> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, category);
                ps.setString(2, escapeFts5Phrase(keyword));
                ps.setInt(3, SEARCH_LIMIT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TextIndexData item = new TextIndexData();
                        item.setId(rs.getString("id"));
                        item.setCategory(rs.getString("category"));
                        item.setText(rs.getString("text"));
                        result.add(item);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            logger.error("Error while searching", e);
            return new ArrayList<>();
        } finally {
            conn.close();
        }
    }

    /**
     * Escape keyword for FTS5 phrase match: wrap in double quotes for phrase search.
     */
    private static String escapeFts5Phrase(String keyword) {
        if (keyword == null) return "\"\"";
        String escaped = keyword.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    @Override
    public boolean delete(String category) {
        if (category == null) {
            return false;
        }
        try (Conn conn = new Conn(connName)) {
            String sql = "DELETE FROM " + FTS_TABLE_NAME + " WHERE category = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, category);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.error("Error while deleting category {}", category, e);
            return false;
        }
    }
}
