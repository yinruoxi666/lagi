package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregates for a time window: total usage, total saved, daily average (total usage / calendar days, rounded).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsSummary {
    private TokenStatisticsRange range;
    /** Sum of {@code total_tokens} in the window. */
    private long totalTokensConsumed;
    /** Sum of {@code saved_tokens} in the window. */
    private long totalSavedTokens;
    /** Average token usage per calendar day in the window. */
    private long dailyAvgTokensConsumed;
    /** Number of detail rows aggregated. */
    private long recordCount;
}
