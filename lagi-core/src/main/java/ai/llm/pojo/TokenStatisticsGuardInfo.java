package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Home-page "guard" stats derived from the earliest persisted token usage row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsGuardInfo {
    /**
     * Inclusive calendar days from the first record's local date through today (0 if no data).
     */
    private long guardDays;
    /** Epoch millis of the earliest {@code created_at}; 0 if none. */
    private long firstRecordAt;
}
