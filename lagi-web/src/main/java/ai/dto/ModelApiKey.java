package ai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ModelApiKey {
    private Long id;
    private String name;
    private String apiKey;
    private Date createdTime;
    private String userId;
    private Integer status;
}
