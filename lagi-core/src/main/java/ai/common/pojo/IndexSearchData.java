package ai.common.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode
public class IndexSearchData {
    private String id;
    private String text;
    private String category;
    private Long seq;
    private String fileId;
    private List<String> filename;
    private List<String> filepath;
    private Float distance;
    private String image;
    private List<String> imageList;
    private String level;
    private String parentId;
    private String source;
    private Map<String, Object> metadata;
    private String refQuestion;
}
