package ai.llm.pojo;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OpenAiResponseTextInput implements OpenAiResponseInput {
    private String text;

    public OpenAiResponseTextInput() {
    }

    public OpenAiResponseTextInput(String text) {
        this.text = text;
    }

}
