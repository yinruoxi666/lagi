package ai.bigdata.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Query-side keyword statistics used to explain sparse retrieval. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryKeywordScore {
    private String keyword;
    @JsonProperty("query_frequency")
    private Integer queryFrequency;
    @JsonProperty("document_frequency")
    private Long documentFrequency;
    private Double score;
}
