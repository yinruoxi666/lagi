package ai.vector.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
public class ChromaGetRequest {
    
    /**
     * List of document IDs to retrieve
     */
    private List<String> ids;
    
    /**
     * Filter conditions for metadata
     */
    private Map<String, Object> where;
    
    /**
     * Filter conditions for document content
     */
    private Map<String, Object> whereDocument;
    
    /**
     * Sort order for results
     */
    private String sort;
    
    /**
     * Maximum number of results to return
     */
    private Integer limit;
    
    /**
     * Number of results to skip (for pagination)
     */
    private Integer offset;
    
    /**
     * List of data types to include in response
     * Possible values: "metadatas", "documents", "embeddings", "distances"
     */
    private List<String> include;
}
