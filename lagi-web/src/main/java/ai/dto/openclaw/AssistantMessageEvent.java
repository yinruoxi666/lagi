package ai.dto.openclaw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AssistantMessageEvent.Start.class, name = "start"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.TextStart.class, name = "text_start"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.TextDelta.class, name = "text_delta"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.TextEnd.class, name = "text_end"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingStart.class, name = "thinking_start"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingDelta.class, name = "thinking_delta"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingEnd.class, name = "thinking_end"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallStart.class, name = "toolcall_start"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallDelta.class, name = "toolcall_delta"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallEnd.class, name = "toolcall_end"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.Done.class, name = "done"),
        @JsonSubTypes.Type(value = AssistantMessageEvent.Error.class, name = "error")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AssistantMessageEvent {

    public abstract String getType();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Start extends AssistantMessageEvent {
        private AssistantMessage partial;

        @Override
        public String getType() { return "start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextStart extends AssistantMessageEvent {
        private int contentIndex;
        private AssistantMessage partial;

        @Override
        public String getType() { return "text_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextDelta extends AssistantMessageEvent {
        private int contentIndex;
        private String delta;
        private AssistantMessage partial;

        @Override
        public String getType() { return "text_delta"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextEnd extends AssistantMessageEvent {
        private int contentIndex;
        private String content;
        private AssistantMessage partial;

        @Override
        public String getType() { return "text_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThinkingStart extends AssistantMessageEvent {
        private int contentIndex;
        private AssistantMessage partial;

        @Override
        public String getType() { return "thinking_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThinkingDelta extends AssistantMessageEvent {
        private int contentIndex;
        private String delta;
        private AssistantMessage partial;

        @Override
        public String getType() { return "thinking_delta"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThinkingEnd extends AssistantMessageEvent {
        private int contentIndex;
        private String content;
        private AssistantMessage partial;

        @Override
        public String getType() { return "thinking_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallStart extends AssistantMessageEvent {
        private int contentIndex;
        private AssistantMessage partial;

        @Override
        public String getType() { return "toolcall_start"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallDelta extends AssistantMessageEvent {
        private int contentIndex;
        private String delta;
        private AssistantMessage partial;

        @Override
        public String getType() { return "toolcall_delta"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallEnd extends AssistantMessageEvent {
        private int contentIndex;
        private ToolCall toolCall;
        private AssistantMessage partial;

        @Override
        public String getType() { return "toolcall_end"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Done extends AssistantMessageEvent {
        private StopReason reason;
        private AssistantMessage message;

        @Override
        public String getType() { return "done"; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error extends AssistantMessageEvent {
        private StopReason reason;
        private AssistantMessage error;

        @Override
        public String getType() { return "error"; }
    }
}
