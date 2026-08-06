package ai.vector.pojo;

import ai.openai.pojo.ChatMessage;
import com.fasterxml.jackson.annotation.JsonAlias;
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
public class HybridMetadataSearchRequest {
    private String category;
    private String text;
    @JsonAlias("message")
    private List<ChatMessage> messages;
    private Map<String, Object> where;
    @JsonProperty("where_document")
    private Map<String, Object> whereDocument;
    @JsonProperty("dense_top_k")
    private Integer denseTopK;
    @JsonProperty("bm25_top_k")
    private Integer bm25TopK;
    @JsonProperty("fusion_top_k")
    private Integer fusionTopK;
    @JsonProperty("final_top_k")
    private Integer finalTopK;
    @JsonProperty("rrf_k")
    private Integer rrfK;
    @JsonProperty("dense_weight")
    private Double denseWeight;
    @JsonProperty("bm25_weight")
    private Double bm25Weight;
    private Boolean rerank;
    @JsonProperty("rerank_model")
    private String rerankModel;
}
