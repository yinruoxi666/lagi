package ai.vector.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndexRecord {
    private String document;
    private String id;
    private Map<String, Object> metadata;
    private Float distance;
    private List<Float> embeddings;
}
