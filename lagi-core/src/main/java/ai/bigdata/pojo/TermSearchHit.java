package ai.bigdata.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A ranked hit returned by a term-search backend.
 *
 * <p>The score is backend-specific and is intentionally not compared with a
 * vector distance. Hybrid retrieval uses {@link #rank} for rank fusion.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermSearchHit {
    private String id;
    private String text;
    private Double score;
    private Integer rank;
}
