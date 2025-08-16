package ai.rerank.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank response POJO for document reranking operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResponse {
    
    /**
     * Unique identifier for the rerank request
     */
    private String id;
    
    /**
     * The reranking model used
     */
    private String model;
    
    /**
     * Usage statistics for the rerank operation
     */
    private Usage usage;
    
    /**
     * List of reranked results
     */
    private List<RerankResult> results;
    
    /**
     * Usage statistics for the rerank operation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        
        /**
         * Total number of tokens processed
         */
        private Integer totalTokens;
    }
    
    /**
     * Individual rerank result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankResult {
        
        /**
         * Index of the document in the original list
         */
        private Integer index;
        
        /**
         * The document content
         */
        private Document document;
        
        /**
         * Relevance score for the document
         */
        private Double relevanceScore;
    }
    
    /**
     * Document content
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        
        /**
         * The text content of the document
         */
        private String text;
    }
}
