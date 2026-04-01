package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage {

    private int input;

    private int output;

    private int cacheRead;

    private int cacheWrite;

    private int totalTokens;

    private UsageCost cost;
}
