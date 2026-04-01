package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base type for all agent messages.
 * Corresponds to TypeScript: AgentMessage = Message | CustomAgentMessages[keyof CustomAgentMessages]
 * where Message = UserMessage | AssistantMessage | ToolResultMessage.
 *
 * Extend @JsonSubTypes to register custom message types via declaration merging equivalent.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ToolResultMessage.class, name = "toolResult")
})
public interface AgentMessage {
    String getRole();
    long getTimestamp();
}
