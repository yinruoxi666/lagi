package ai.bigdata.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Complete, explainable response from one sparse-search backend. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermSearchResponse {
    private String backend;
    private boolean successful;
    private boolean fallbackUsed;
    private String failureReason;
    @Builder.Default
    private List<QueryKeywordScore> queryKeywords = new ArrayList<>();
    @Builder.Default
    private List<TermSearchHit> hits = new ArrayList<>();

    public static TermSearchResponse success(String backend,
                                             List<QueryKeywordScore> queryKeywords,
                                             List<TermSearchHit> hits) {
        return TermSearchResponse.builder()
                .backend(backend)
                .successful(true)
                .queryKeywords(queryKeywords == null ? new ArrayList<>() : queryKeywords)
                .hits(hits == null ? new ArrayList<>() : hits)
                .build();
    }

    public static TermSearchResponse failure(String backend, String reason) {
        return TermSearchResponse.builder()
                .backend(backend)
                .successful(false)
                .failureReason(reason)
                .build();
    }
}
