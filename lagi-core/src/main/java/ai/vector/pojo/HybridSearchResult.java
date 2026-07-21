package ai.vector.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchResult {
    private String id;
    private String document;
    private Map<String, Object> metadata;
    private Float distance;
    @JsonProperty("term_score")
    private Double termScore;
    @JsonProperty("dense_rank")
    private Integer denseRank;
    @JsonProperty("sparse_rank")
    private Integer sparseRank;
    @JsonProperty("fusion_score")
    private Double fusionScore;
    @JsonProperty("rerank_score")
    private Double rerankScore;
}
