package ai.llm.dao;

import ai.llm.pojo.TokenStatisticsDetail;
import ai.llm.pojo.TokenStatisticsGuardInfo;
import ai.llm.pojo.TokenStatisticsPageResult;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsSummary;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class TokenStatisticsDao {

    private static final String DB_URL = "jdbc:sqlite:saas.db";

    private static final String CREATE_SQL = ""
            + "CREATE TABLE IF NOT EXISTS llm_token_statistics ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " created_at INTEGER NOT NULL,"
            + " prompt_tokens INTEGER NOT NULL,"
            + " completion_tokens INTEGER NOT NULL,"
            + " total_tokens INTEGER NOT NULL,"
            + " saved_tokens INTEGER NOT NULL"
            + ");";

    private static final String INDEX_CREATED_AT = ""
            + "CREATE INDEX IF NOT EXISTS idx_llm_token_statistics_created_at ON llm_token_statistics(created_at);";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                try (PreparedStatement ps = conn.prepareStatement(CREATE_SQL)) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(INDEX_CREATED_AT)) {
                    ps.executeUpdate();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            log.error("init llm_token_statistics table failed", e);
        }
    }

    /**
     * Estimated saved tokens: {@code total_tokens} multiplied by a random ratio in [25%, 35%), then rounded to long.
     */
    public static long computeSavedTokens(long totalTokens) {
        if (totalTokens <= 0) {
            return 0;
        }
        double ratio = 0.25 + ThreadLocalRandom.current().nextDouble() * 0.10;
        return Math.round(totalTokens * ratio);
    }

    /**
     * Earliest usage timestamp in the table; {@code 0} if there are no rows.
     */
    public long earliestCreatedAtMillis() {
        String sql = "SELECT MIN(created_at) FROM llm_token_statistics";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Object o = rs.getObject(1);
                if (o == null) {
                    return 0L;
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("query min created_at failed", e);
        }
        return 0L;
    }

    /**
     * Inclusive calendar days from the first record's local date through today; {@code 0} if no data.
     */
    public long guardDaysInclusive() {
        long min = earliestCreatedAtMillis();
        if (min <= 0) {
            return 0L;
        }
        return guardDaysInclusiveFromMin(min);
    }

    private static long guardDaysInclusiveFromMin(long minMs) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = Instant.ofEpochMilli(minMs).atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        long between = ChronoUnit.DAYS.between(first, today);
        if (between < 0) {
            return 0L;
        }
        return between + 1;
    }

    public TokenStatisticsGuardInfo guardInfo() {
        long min = earliestCreatedAtMillis();
        if (min <= 0) {
            return TokenStatisticsGuardInfo.builder().guardDays(0L).firstRecordAt(0L).build();
        }
        return TokenStatisticsGuardInfo.builder()
                .guardDays(guardDaysInclusiveFromMin(min))
                .firstRecordAt(min)
                .build();
    }

    public void insert(long promptTokens, long completionTokens, long totalTokens, long savedTokens) {
        String sql = "INSERT INTO llm_token_statistics (created_at, prompt_tokens, completion_tokens, total_tokens, saved_tokens) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, promptTokens);
            ps.setLong(3, completionTokens);
            ps.setLong(4, totalTokens);
            ps.setLong(5, savedTokens);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert token statistics failed", e);
        }
    }

    /**
     * Aggregate: total token usage, total saved, daily average (total usage / calendar days in window), row count.
     */
    public TokenStatisticsSummary summarize(TokenStatisticsRange range) {
        long[] bounds = millisBounds(range);
        long start = bounds[0];
        long end = bounds[1];
        String sql = "SELECT COUNT(1), COALESCE(SUM(total_tokens), 0), COALESCE(SUM(saved_tokens), 0) FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long recordCount = rs.getLong(1);
                    long totalConsumed = rs.getLong(2);
                    long totalSaved = rs.getLong(3);
                    int days = range.getCalendarSpanDays();
                    long dailyAvg = days <= 0 ? 0 : Math.round((double) totalConsumed / (double) days);
                    return TokenStatisticsSummary.builder()
                            .range(range)
                            .totalTokensConsumed(totalConsumed)
                            .totalSavedTokens(totalSaved)
                            .dailyAvgTokensConsumed(dailyAvg)
                            .recordCount(recordCount)
                            .build();
                }
            }
        } catch (SQLException e) {
            log.error("summarize token statistics failed", e);
        }
        return TokenStatisticsSummary.builder()
                .range(range)
                .totalTokensConsumed(0)
                .totalSavedTokens(0)
                .dailyAvgTokensConsumed(0)
                .recordCount(0)
                .build();
    }

    /**
     * Paginated detail rows, ordered by {@code id} descending (newest first). {@code page} is 1-based.
     */
    public TokenStatisticsPageResult queryDetails(TokenStatisticsRange range, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.min(500, Math.max(1, pageSize));
        long[] bounds = millisBounds(range);
        long start = bounds[0];
        long end = bounds[1];
        long total = countInRange(start, end);
        if (total == 0) {
            return TokenStatisticsPageResult.empty(range, p, size);
        }
        int offset = (p - 1) * size;
        String sql = "SELECT id, created_at, prompt_tokens, completion_tokens, total_tokens, saved_tokens FROM llm_token_statistics WHERE created_at >= ? AND created_at < ? ORDER BY id DESC LIMIT ? OFFSET ?";
        List<TokenStatisticsDetail> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            ps.setInt(3, size);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(TokenStatisticsDetail.builder()
                            .id(rs.getLong("id"))
                            .createdAt(rs.getLong("created_at"))
                            .promptTokens(rs.getLong("prompt_tokens"))
                            .completionTokens(rs.getLong("completion_tokens"))
                            .totalTokens(rs.getLong("total_tokens"))
                            .savedTokens(rs.getLong("saved_tokens"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics details failed", e);
        }
        return TokenStatisticsPageResult.builder()
                .range(range)
                .page(p)
                .pageSize(size)
                .total(total)
                .records(list)
                .build();
    }

    /**
     * Row count of detail records in the given time window.
     */
    public long countDetails(TokenStatisticsRange range) {
        long[] bounds = millisBounds(range);
        return countInRange(bounds[0], bounds[1]);
    }

    private long countInRange(long startMs, long endMsExclusive) {
        String sql = "SELECT COUNT(1) FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("count token statistics failed", e);
        }
        return 0;
    }

    /**
     * Time bounds for {@code created_at}: half-open {@code [start, end)}.
     * For {@link TokenStatisticsRange#ALL}, uses {@code [0, Long#MAX_VALUE)}.
     */
    public static long[] millisBounds(TokenStatisticsRange range) {
        if (range == TokenStatisticsRange.ALL) {
            return new long[]{0L, Long.MAX_VALUE};
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(range.getCalendarSpanDays() - 1);
        long startMs = first.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new long[]{startMs, endMs};
    }
}
