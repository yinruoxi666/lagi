package ai.vector.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddChunkEmbedding {
    private String category;
    private List<AddChunkEmbeddingData> data;

    @Data
    public static class AddChunkEmbeddingData {
        private String id;
        private String document;
        @JsonProperty("parent_id")
        private String parentId;
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("extra_metadatas")
        private Map<String, Object> extraMetadatas;
    }
}
