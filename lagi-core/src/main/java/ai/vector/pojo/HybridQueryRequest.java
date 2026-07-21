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
public class HybridQueryRequest {
    private String category;
    private String text;
    private Map<String, Object> where;
    @JsonProperty("where_document")
    private Map<String, Object> whereDocument;
    @JsonProperty("dense_top_k")
    private Integer denseTopK;
    @JsonProperty("sparse_top_k")
    private Integer sparseTopK;
    @JsonProperty("fusion_top_k")
    private Integer fusionTopK;
    @JsonProperty("final_top_k")
    private Integer finalTopK;
    @JsonProperty("rrf_k")
    private Integer rrfK;
    @JsonProperty("dense_weight")
    private Double denseWeight;
    @JsonProperty("sparse_weight")
    private Double sparseWeight;
}
