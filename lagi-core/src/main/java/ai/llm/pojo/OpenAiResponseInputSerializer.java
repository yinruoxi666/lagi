package ai.llm.pojo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class OpenAiResponseInputSerializer extends StdSerializer<OpenAiResponseInput> {

    public OpenAiResponseInputSerializer() {
        super(OpenAiResponseInput.class);
    }

    @Override
    public void serialize(OpenAiResponseInput value, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
        if (value instanceof OpenAiResponseTextInput) {
            OpenAiResponseTextInput textInput = (OpenAiResponseTextInput) value;
            gen.writeString(textInput.getText());
        } else if (value instanceof OpenAiResponseInputList) {
            OpenAiResponseInputList list = (OpenAiResponseInputList) value;
            gen.writeStartArray();
            if (list.getItems() != null) {
                for (OpenAiResponseInputItem item : list.getItems()) {
                    gen.writeObject(item);
                }
            }
            gen.writeEndArray();
        } else {
            throw new IllegalStateException("Unknown Input implementation: " + value.getClass());
        }
    }
}
