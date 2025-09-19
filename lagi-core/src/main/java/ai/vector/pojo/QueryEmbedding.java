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
public class QueryEmbedding {
    private String category;
    private Map<String, Object> where;
    @JsonProperty("where_document")
    private Map<String, Object> whereDocument;
    private Integer n;
    private String text;
}
