package ai.llm.pojo;

import lombok.Data;

@Data
public class OpenAiResponseTextContextItem implements OpenAiResponseContextItem ,OpenAiResponseInputItem{
    private String type; // 例如 "input_text"
    private String text;

    public OpenAiResponseTextContextItem() {
    }

    public OpenAiResponseTextContextItem(String text) {
        this.type = "input_text";
        this.text = text;
    }

    public OpenAiResponseTextContextItem(String type, String text) {
        this.type = type;
        this.text = text;
    }

}
