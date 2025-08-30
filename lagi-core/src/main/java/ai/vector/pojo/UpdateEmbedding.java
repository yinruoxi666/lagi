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
public class UpdateEmbedding {
    private String category;
    private List<UpdateEmbeddingData> data;

    @Data
    public static class UpdateEmbeddingData {
        private String id;
        private String document;
        private Map<String, Object> metadata;
        private List<Float> embedding;
    }
}
