package ai.vector.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.amikos.chromadb.model.QueryEmbedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chroma Get Request POJO
 * Represents a request to retrieve documents from Chroma vector database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChromaQueryRequest {
    private Map<String, Object> where;
    @JsonProperty("where_document")
    private Map<String, Object> whereDocument;
    @JsonProperty("query_embeddings")
    private List<List<Float>> queryEmbeddings;
    @JsonProperty("n_results")
    private Integer nResults;
    private List<String> include;
}
