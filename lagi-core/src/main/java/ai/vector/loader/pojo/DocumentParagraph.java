package ai.vector.loader.pojo;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class DocumentParagraph {

    /**
     *  txt image table video ...
     */
    private String type;

    /**
     * heading 1 ....
     */
    private String subType;

    /**
     * 1 2 3 4 5 6
     */
    private String level;

    private String txt;

    private List<Image> images;

    private List<List<String>> table;

    @Data
    public static class Image {
        private String path;
        private Integer offset;
    }
}
