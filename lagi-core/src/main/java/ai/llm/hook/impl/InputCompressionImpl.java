package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.pojo.IntentResult;
import ai.llm.hook.BeforeModel;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;

import java.util.ArrayList;
import java.util.List;

@Order
@Component
public class InputCompressionImpl implements BeforeModel {

    private final SampleIntentServiceImpl intentService = new SampleIntentServiceImpl();

    @Override
    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        IntentResult intentResult = intentService.detectSegmentationBoundary(request);
        Integer continuedIndex = intentResult.getContinuedIndex();
        List<ChatMessage> chatMessages = request.getMessages();
        if(chatMessages.isEmpty()) {
            return request;
        }
        boolean hasSystem = LagiGlobal.LLM_ROLE_SYSTEM.equals(chatMessages.get(0).getRole());
        List<ChatMessage> newChatMessages = new ArrayList<>();
        if(hasSystem) {
            newChatMessages.add(chatMessages.get(0));
        }
        if(continuedIndex != null) {
            newChatMessages.addAll(chatMessages.subList(continuedIndex, chatMessages.size()));
        } else {
            newChatMessages.addAll(chatMessages.subList(chatMessages.size() - 1, chatMessages.size()));
        }
        request.setMessages(newChatMessages);
        System.out.println(newChatMessages);
        return request;
    }

}
