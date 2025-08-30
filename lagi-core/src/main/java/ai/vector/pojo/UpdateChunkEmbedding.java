package ai.vector.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UpdateChunkEmbedding {
    private String category;
    private List<UpdateChunkEmbeddingData> data;

    @Data
    public static class UpdateChunkEmbeddingData {
        private String id;
        private String document;
    }
}
