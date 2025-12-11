package ai.llm.pojo;

import java.util.List;

public class OpenAiResponseInputMessage implements OpenAiResponseInputItem {
    private String role;               // "user" / "assistant" / "system" / "developer"
    private List<OpenAiResponseContextItem> content; // 文本 / 图片 / 音频等内容

    public OpenAiResponseInputMessage() {
    }

    public OpenAiResponseInputMessage(String role, List<OpenAiResponseContextItem> content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<OpenAiResponseContextItem> getContent() {
        return content;
    }

    public void setContent(List<OpenAiResponseContextItem> content) {
        this.content = content;
    }
}
