package ai.common.pojo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Builder
public class FileChunkResponse {
    private String status;
    private List<Document> data;
    private String msg;

    @Data
    @EqualsAndHashCode
    public static class Document {
        private String text;
        private List<Image> images;
        private String source;
        private Integer order;
        private String referenceDocumentId;
    }

    @Data
    public static class Image {
        private String path;
    }
}
