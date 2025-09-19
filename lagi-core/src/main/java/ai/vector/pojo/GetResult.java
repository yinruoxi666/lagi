package ai.vector.pojo;


import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GetResult {
    private List<String> documents;
    private List<List<Float>> embeddings;
    private List<String> ids;
    private List<Map<String, Object>> metadatas;
}
