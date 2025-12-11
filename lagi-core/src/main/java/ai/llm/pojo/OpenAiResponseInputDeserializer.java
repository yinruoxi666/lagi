package ai.llm.pojo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OpenAiResponseInputDeserializer extends JsonDeserializer<OpenAiResponseInput> {
    @Override
    public OpenAiResponseInput deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonToken token = p.getCurrentToken();
        if (token == null) {
        token = p.nextToken();
    }

        if (token == JsonToken.VALUE_STRING) {
        // input 是纯字符串
        String text = p.getValueAsString();
        return new OpenAiResponseTextInput(text);
    } else if (token == JsonToken.START_ARRAY) {
        // input 是数组
        ObjectCodec codec = p.getCodec();
        ArrayNode node = codec.readTree(p);

        if (!node.isArray()) {
            ctxt.reportInputMismatch(OpenAiResponseInput.class,
                    "Expected array for Input, got: %s", node.getNodeType());
        }

        List<OpenAiResponseInputItem> items = new ArrayList< OpenAiResponseInputItem >();

        for (JsonNode itemNode : node) {
            // 简单分派逻辑，你可以按 type 再精细化
            if (itemNode.has("role")) {
                // 当成 InputMessage
                OpenAiResponseInputMessage msg = codec.treeToValue(itemNode, OpenAiResponseInputMessage.class);
                items.add(msg);
            } else if (itemNode.has("id") && itemNode.has("type")) {
                // 当成 ItemRef（也可以根据 type 值更严格判断）
                OpenAiResponseItemRef ref = codec.treeToValue(itemNode, OpenAiResponseItemRef.class);
                items.add(ref);
            } else {
                // 默认当成 TextContextItem
                OpenAiResponseTextContextItem textItem = codec.treeToValue(itemNode, OpenAiResponseTextContextItem.class);
                items.add(textItem);
            }
        }

        return new OpenAiResponseInputList(items);
    } else {
        ctxt.reportInputMismatch(
                OpenAiResponseInput.class,
                "input must be either a string or an array, but got token: %s",
                token
        );
        return null; // 理论上不会走到这里
    }
}
}
