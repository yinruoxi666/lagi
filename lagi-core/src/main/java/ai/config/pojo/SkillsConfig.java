package ai.config.pojo;

import ai.pnps.skills.pojo.SkillEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class SkillsConfig {
    private List<String> roots;
    private String workspace;
    private String rule = "cli";
    @JsonProperty("items")
    private List<SkillEntry> skills;

    private List<WorkerConfig> workers;
    private List<PnpConfig> pnps;

}
