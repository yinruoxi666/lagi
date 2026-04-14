package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class FiltersConfig {
    private Boolean enable = true;
    @JsonProperty("items")
    private List<FilterConfig> items;
}
