package ai.vector.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridMetadataSearchResult {
    private String id;
    private String text;
    private Map<String, Object> metadata;
    @JsonProperty("hybrid_score")
    private Double hybridScore;
    private Bm25Evidence bm25;
    private EmbeddingEvidence embedding;
    private RerankEvidence rerank;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bm25Evidence {
        private Double score;
        private Integer rank;
        @JsonProperty("matched_keywords")
        private List<String> matchedKeywords;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingEvidence {
        private Float distance;
        private Integer rank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankEvidence {
        private Integer rank;
        private Double score;
    }
}
