package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RoutersConfig {
    Boolean enable = true;
    @JsonProperty("items")
    List<RouterConfig> routerItems;
}
