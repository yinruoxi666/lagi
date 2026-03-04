package ai.openai.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Property {
    private String type;
    private String description;
    @SerializedName("enum")
    @JsonProperty("enum")
    private List<String> enums;
    private Parameters items;
    private List<Map<String,Object>> anyOf;
    @JsonProperty("default")
    private Object defaultValue;
    private String title;
}
