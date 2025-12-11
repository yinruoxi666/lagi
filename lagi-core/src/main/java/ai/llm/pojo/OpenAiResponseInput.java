package ai.llm.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize(using = OpenAiResponseInputSerializer.class)
@JsonDeserialize(using = OpenAiResponseInputDeserializer.class)
public interface OpenAiResponseInput {

    static OpenAiResponseInput ofText(String text) {
        return new OpenAiResponseTextInput(text);
    }

    static OpenAiResponseInput ofItems(List<OpenAiResponseInputItem> items) {
        return new OpenAiResponseInputList(items);
    }
}
