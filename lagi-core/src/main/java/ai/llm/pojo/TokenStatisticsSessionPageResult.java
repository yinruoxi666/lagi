package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsSessionPageResult {
    private TokenStatisticsRange range;
    private int page;
    private int pageSize;
    private long total;
    private List<TokenStatisticsSessionItem> records;

    public static TokenStatisticsSessionPageResult empty(TokenStatisticsRange range, int page, int pageSize) {
        return TokenStatisticsSessionPageResult.builder()
                .range(range)
                .page(page)
                .pageSize(pageSize)
                .total(0)
                .records(Collections.emptyList())
                .build();
    }
}
