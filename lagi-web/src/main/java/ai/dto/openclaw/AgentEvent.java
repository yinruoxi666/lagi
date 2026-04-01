package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentEvent.AgentStart.class, name = "agent_start"),
        @JsonSubTypes.Type(value = AgentEvent.AgentEnd.class, name = "agent_end"),
        @JsonSubTypes.Type(value = AgentEvent.TurnStart.class, name = "turn_start"),
        @JsonSubTypes.Type(value = AgentEvent.TurnEnd.class, name = "turn_end"),
        @JsonSubTypes.Type(value = AgentEvent.MessageStart.class, name = "message_start"),
        @JsonSubTypes.Type(value = AgentEvent.MessageUpdate.class, name = "message_update"),
        @JsonSubTypes.Type(value = AgentEvent.MessageEnd.class, name = "message_end"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStart.class, name = "tool_execution_start"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionUpdate.class, name = "tool_execution_update"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionEnd.class, name = "tool_execution_end")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AgentEvent {

    public abstract String getType();

    @Data
    @Builder
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentStart extends AgentEvent {
        @Override
        public String getType() { return "agent_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentEnd extends AgentEvent {
        private List<AgentMessage> messages;

        @Override
        public String getType() { return "agent_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TurnStart extends AgentEvent {
        @Override
        public String getType() { return "turn_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TurnEnd extends AgentEvent {
        private AgentMessage message;
        private List<ToolResultMessage> toolResults;

        @Override
        public String getType() { return "turn_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageStart extends AgentEvent {
        private AgentMessage message;

        @Override
        public String getType() { return "message_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageUpdate extends AgentEvent {
        private AgentMessage message;
        private AssistantMessageEvent assistantMessageEvent;

        @Override
        public String getType() { return "message_update"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageEnd extends AgentEvent {
        private AgentMessage message;

        @Override
        public String getType() { return "message_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolExecutionStart extends AgentEvent {
        private String toolCallId;
        private String toolName;
        private Object args;

        @Override
        public String getType() { return "tool_execution_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolExecutionUpdate extends AgentEvent {
        private String toolCallId;
        private String toolName;
        private Object args;
        private Object partialResult;

        @Override
        public String getType() { return "tool_execution_update"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolExecutionEnd extends AgentEvent {
        private String toolCallId;
        private String toolName;
        private Object result;
        private boolean isError;

        @Override
        public String getType() { return "tool_execution_end"; }
    }
}
