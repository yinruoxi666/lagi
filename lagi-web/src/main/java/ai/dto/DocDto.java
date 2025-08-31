package ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocDto {
    private String vectorId;
    private String content;
    private String status;
    private Integer index;
    private List<String> questions;
}
