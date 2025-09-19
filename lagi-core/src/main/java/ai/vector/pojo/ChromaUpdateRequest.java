package ai.vector.pojo;

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
public class ChromaUpdateRequest {
    private List<String> ids;
    private List<List<Float>> embeddings;
    private List<Map<String, Object>> metadatas;
    private List<String> documents;
}
