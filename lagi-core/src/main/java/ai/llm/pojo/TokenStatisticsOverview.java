package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact aggregate for APIs: sums, averages, and row count in the time window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsOverview {
    /** Echo of the requested range key (e.g. {@code 7d}, {@code all}). */
    private String range;
    /** Sum of {@code total_tokens} in the window. */
    private long totalTokens;
    /** Sum of {@code saved_tokens} in the window. */
    private long totalSavedTokens;
    /** Average token usage per calendar day in the window (0 for {@link ai.llm.pojo.TokenStatisticsRange#ALL}). */
    private long dailyAvgTokens;
    /** Number of stored usage rows in the window. */
    private long recordCount;
}
