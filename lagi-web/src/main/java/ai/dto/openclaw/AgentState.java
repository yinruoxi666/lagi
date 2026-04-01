package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentState {

    private String systemPrompt;

    private Object model;

    private ThinkingLevel thinkingLevel;

    private List<AgentTool> tools;

    private List<AgentMessage> messages;

    private boolean isStreaming;

    private AgentMessage streamMessage;

    private Set<String> pendingToolCalls;

    private String error;
}
