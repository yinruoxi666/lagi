package ai.bigdata.impl;

import ai.bigdata.IBigdata;
import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;
import ai.common.db.Conn;
import ai.config.pojo.BigdataConfig;
import ai.utils.AiGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite FTS5-based implementation of IBigdata.
 * Uses a trigram-tokenized FTS5 virtual table so Chinese phrases and
 * substrings can be retrieved without language-specific word segmentation.
 */
public class SqliteSearchAdapter implements IBigdata {
    private static final Logger logger = LoggerFactory.getLogger(SqliteSearchAdapter.class);
    // Keep the v1 unicode61 table intact. The versioned name makes the
    // tokenizer migration non-destructive and explicit.
    private static final String FTS_TABLE_NAME = "fts_text_index_v2";

    private final String connName;

    public SqliteSearchAdapter(BigdataConfig config) {
        this.connName = AiGlobal.DEFAULT_DB;
        ensureFtsTable();
    }

    private void ensureFtsTable() {
        String sql = "CREATE VIRTUAL TABLE IF NOT EXISTS " + FTS_TABLE_NAME
                + " USING fts5(id UNINDEXED, category UNINDEXED, text, tokenize='trigram')";
        try (Conn conn = new Conn(connName); Statement statement = conn.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite FTS5 term index", e);
        }
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
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error while rolling back transaction: {}", ex.getMessage());
                }
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
    public List<TermSearchHit> search(String keyword, String category, int topK) {
        if (keyword == null || keyword.isEmpty() || category == null || topK <= 0) {
            return new ArrayList<>();
        }
        Conn conn = new Conn(connName);
        try {
            String sql = "SELECT id, text, bm25(" + FTS_TABLE_NAME + ") AS term_score FROM "
                    + FTS_TABLE_NAME + " WHERE category = ? AND " + FTS_TABLE_NAME
                    + " MATCH ? ORDER BY term_score ASC LIMIT ?";
            List<TermSearchHit> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, category);
                ps.setString(2, escapeFts5Phrase(keyword));
                ps.setInt(3, topK);
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        result.add(TermSearchHit.builder()
                                .id(rs.getString("id"))
                                .text(rs.getString("text"))
                                // FTS5 bm25 is lower-is-better (commonly negative).
                                .score(-rs.getDouble("term_score"))
                                .rank(rank++)
                                .build());
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

    @Override
    public boolean delete(String category, List<String> ids) {
        if (category == null || ids == null || ids.isEmpty()) {
            return false;
        }
        Conn conn = null;
        try {
            conn = new Conn(connName);
            conn.setAutoCommit(false);
            String sql = "DELETE FROM " + FTS_TABLE_NAME + " WHERE category = ? AND id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String id : ids) {
                    ps.setString(1, category);
                    ps.setString(2, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    logger.error("Error while rolling back term-index deletion", rollbackError);
                }
            }
            logger.error("Error while deleting term index ids from category {}", category, e);
            return false;
        } finally {
            if (conn != null) {
                conn.close();
            }
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
