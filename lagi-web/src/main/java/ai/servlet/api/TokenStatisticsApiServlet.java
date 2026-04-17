package ai.servlet.api;

import ai.llm.dao.TokenStatisticsDao;
import ai.llm.pojo.TokenStatisticsGuardInfo;
import ai.llm.pojo.TokenStatisticsOverview;
import ai.llm.pojo.TokenStatisticsPageResult;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsSessionPageResult;
import ai.llm.pojo.TokenStatisticsSummary;
import ai.servlet.BaseServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP API for persisted LLM token usage: aggregate counts and paginated detail rows.
 * <p>
 * {@code GET /v1/token-statistics/overview?range=...} &mdash; total tokens and row count.<br>
 * {@code GET /v1/token-statistics/details?range=...&page=...&pageSize=...} &mdash; paginated rows.<br>
 * {@code GET /v1/token-statistics/guard} &mdash; guard days from earliest token record to today.
 * </p>
 * Query {@code range}: {@code today} (default), {@code 7d}, {@code 30d}, {@code all}.
 */
public class TokenStatisticsApiServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final TokenStatisticsDao tokenStatisticsDao = new TokenStatisticsDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        String uri = req.getRequestURI();
        try {
            if (uri.endsWith("/overview")) {
                writeOverview(req, resp);
            } else if (uri.endsWith("/details")) {
                writeDetails(req, resp);
            } else if (uri.endsWith("/sessions")) {
                writeSessions(req, resp);
            } else if (uri.endsWith("/guard")) {
                writeGuard(resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeJson(resp, "{\"error\":\"not found\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeErrorJson(resp, "invalid number: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeErrorJson(resp, e.getMessage());
        }
    }

    /**
     * JSON: {@code totalTokens}, {@code totalSavedTokens}, {@code dailyAvgTokens}, {@code recordCount}, {@code range}.
     */
    private void writeOverview(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rangeParam = req.getParameter("range");
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(rangeParam);
        TokenStatisticsSummary summary = tokenStatisticsDao.summarize(range);
        String echo = (rangeParam == null || rangeParam.isEmpty()) ? "today" : rangeParam.trim();
        TokenStatisticsOverview overview = TokenStatisticsOverview.builder()
                .range(echo)
                .totalTokens(summary.getTotalTokensConsumed())
                .totalSavedTokens(summary.getTotalSavedTokens())
                .dailyAvgTokens(summary.getDailyAvgTokensConsumed())
                .recordCount(summary.getRecordCount())
                .build();
        writeJson(resp, gson.toJson(overview));
    }

    /**
     * JSON: {@code guardDays} (inclusive calendar days since first record), {@code firstRecordAt} (epoch millis, 0 if none).
     */
    private void writeGuard(HttpServletResponse resp) throws IOException {
        TokenStatisticsGuardInfo info = tokenStatisticsDao.guardInfo();
        writeJson(resp, gson.toJson(info));
    }

    /**
     * Paginated detail rows; {@code page} defaults to 1, {@code pageSize} defaults to 20.
     */
    private void writeDetails(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rangeParam = req.getParameter("range");
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(rangeParam);
        int page = parsePositiveInt(req.getParameter("page"), 1);
        int pageSize = parsePositiveInt(req.getParameter("pageSize"), 20);
        TokenStatisticsPageResult pageResult = tokenStatisticsDao.queryDetails(range, page, pageSize);
        writeJson(resp, gson.toJson(pageResult));
    }

    private void writeSessions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rangeParam = req.getParameter("range");
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(rangeParam);
        int page = parsePositiveInt(req.getParameter("page"), 1);
        int pageSize = parsePositiveInt(req.getParameter("pageSize"), 20);
        Long startMs = parseNullableLong(req.getParameter("startMs"));
        Long endMs = parseNullableLong(req.getParameter("endMs"));
        TokenStatisticsSessionPageResult pageResult = tokenStatisticsDao.querySessions(range, page, pageSize, startMs, endMs);
        writeJson(resp, gson.toJson(pageResult));
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        int v = Integer.parseInt(raw.trim());
        if (v < 1) {
            throw new IllegalArgumentException("must be >= 1");
        }
        return v;
    }

    private static Long parseNullableLong(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return Long.parseLong(raw.trim());
    }

    private static void writeJson(HttpServletResponse resp, String json) throws IOException {
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }

    private void writeErrorJson(HttpServletResponse resp, String message) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        writeJson(resp, gson.toJson(err));
    }
}
