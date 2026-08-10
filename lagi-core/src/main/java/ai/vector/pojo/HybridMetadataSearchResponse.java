package ai.vector.pojo;

import ai.bigdata.pojo.QueryKeywordScore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridMetadataSearchResponse {
    private String query;
    @JsonProperty("bm25_backend")
    private String bm25Backend;
    @JsonProperty("bm25_fallback_used")
    private boolean bm25FallbackUsed;
    @JsonProperty("bm25_status")
    private String bm25Status;
    @JsonProperty("rerank_status")
    private String rerankStatus;
    @Builder.Default
    @JsonProperty("query_keywords")
    private List<QueryKeywordScore> queryKeywords = new ArrayList<>();
    @Builder.Default
    private List<HybridMetadataSearchResult> results = new ArrayList<>();
}
