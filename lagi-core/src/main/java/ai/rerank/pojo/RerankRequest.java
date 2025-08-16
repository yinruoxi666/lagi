package ai.rerank.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank request POJO for document reranking operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {
    
    /**
     * The reranking model to use
     */
    private String model;
    
    /**
     * The query text for reranking
     */
    private String query;
    
    /**
     * List of documents to be reranked
     */
    private List<String> documents;
}
