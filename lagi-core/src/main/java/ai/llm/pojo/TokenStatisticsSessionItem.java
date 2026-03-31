package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsSessionItem {
    private String sessionId;
    private long requestCount;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long savedTokens;
    private double estimatedCost;
    private long firstRequestAt;
    private long lastRequestAt;
}
