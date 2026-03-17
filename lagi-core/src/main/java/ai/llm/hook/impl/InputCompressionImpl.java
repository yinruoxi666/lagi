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
//@Component
public class InputCompressionImpl implements BeforeModel {

    private final SampleIntentServiceImpl intentService = new SampleIntentServiceImpl();

    @Override
    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        List<ChatMessage> merge = mergeConsecutiveMessages(request.getMessages());
        request.setMessages(merge);
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
        return request;
    }

    private List<ChatMessage> mergeConsecutiveMessages(List<ChatMessage> messages) {
        List<ChatMessage> mergedList = new ArrayList<>();
        if (messages.isEmpty()) {
            return mergedList;
        }

        ChatMessage current = messages.get(0);
        mergedList.add(current);

        for (int i = 1; i < messages.size(); i++) {
            ChatMessage next = messages.get(i);
            ChatMessage lastMerged = mergedList.get(mergedList.size() - 1);

            if (lastMerged.getRole().equals(next.getRole())
                    && (LagiGlobal.LLM_ROLE_USER.equals(next.getRole())
                    || LagiGlobal.LLM_ROLE_ASSISTANT.equals(next.getRole()))) {
                String mergedContent = lastMerged.getContent() + "\n" + next.getContent();
                lastMerged.setContent(mergedContent);
            } else {
                mergedList.add(next);
            }
        }
        return mergedList;
    }

}
