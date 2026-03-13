package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.pojo.IntentResult;
import ai.llm.hook.BeforeModel;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;

import java.util.List;

@Order
@Component
public class InputCompressionImpl implements BeforeModel {

    private final SampleIntentServiceImpl intentService = new SampleIntentServiceImpl();

    @Override
    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        IntentResult intentResult = intentService.detectIntent(request);
        Integer continuedIndex = intentResult.getContinuedIndex();
        if(continuedIndex != null) {
            List<ChatMessage> chatMessages = request.getMessages();
            chatMessages.subList(continuedIndex, chatMessages.size());
            request.setMessages(chatMessages);
        }
        return request;
    }

}
