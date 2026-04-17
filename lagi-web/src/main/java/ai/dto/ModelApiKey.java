package ai.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Date;

@Data
public class ModelApiKey {
    private Long id;
    private String name;
    private String provider;
    private String apiKey;
    private String apiAddress;
    private Date createdTime;
    private String userId;
    private Integer status;
}
