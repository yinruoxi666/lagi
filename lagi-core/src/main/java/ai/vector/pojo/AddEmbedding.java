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
public class AddEmbedding {
    private String category;
    private List<AddEmbeddingData> data;

    @Data
    public static class AddEmbeddingData {
        private String id;
        private List<Float> embedding;
        private Map<String, Object> metadata;
        private String document;
    }
}
