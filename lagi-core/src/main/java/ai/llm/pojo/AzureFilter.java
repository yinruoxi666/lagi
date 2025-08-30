package ai.llm.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AzureFilter {
    private Boolean filtered;
    private String severity;
}
