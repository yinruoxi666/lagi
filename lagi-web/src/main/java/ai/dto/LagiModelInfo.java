package ai.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class LagiModelInfo {
    private int id;
    private String provider;
    private String modelName;
}
