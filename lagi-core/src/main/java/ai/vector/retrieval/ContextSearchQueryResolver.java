package ai.vector.retrieval;

import ai.intent.enums.IntentStatusEnum;
import ai.intent.pojo.IntentResult;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;
import ai.utils.StoppingWordUtil;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Builds the standalone retrieval query used by context-aware vector searches. */
public final class ContextSearchQueryResolver {
    private ContextSearchQueryResolver() {
    }

    public static String resolve(ChatCompletionRequest request, IntentResult intentResult) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("Search messages are required when text is empty");
        }
        String lastMessage = ChatCompletionUtil.getLastMessage(request);
        if (StrUtil.isBlank(lastMessage)) {
            throw new IllegalArgumentException("The last search message must not be empty");
        }
        if (intentResult == null
                || !IntentStatusEnum.CONTINUE.getName().equals(intentResult.getStatus())) {
            return lastMessage;
        }

        List<ChatMessage> messages = request.getMessages();
        Integer continuedIndex = intentResult.getContinuedIndex();
        if (continuedIndex != null && continuedIndex >= 0 && continuedIndex < messages.size()) {
            ChatMessage continuedMessage = messages.get(continuedIndex);
            if (continuedMessage == null || StrUtil.isBlank(continuedMessage.getContent())
                    || LagiGlobal.LLM_ROLE_SYSTEM.equals(continuedMessage.getRole())) {
                return lastMessage;
            }
            String content = continuedMessage.getContent();
            String source = Arrays.stream(content.split("[， ,.。！!?？]"))
                    .filter(StrUtil::isNotBlank)
                    .filter(StoppingWordUtil::containsStoppingWorlds)
                    .findAny()
                    .orElse(content);
            return source + lastMessage;
        }

        List<ChatMessage> userMessages = messages.stream()
                .filter(message -> message != null && LagiGlobal.LLM_ROLE_USER.equals(message.getRole()))
                .filter(message -> StrUtil.isNotBlank(message.getContent()))
                .collect(Collectors.toList());
        if (userMessages.size() > 1) {
            return userMessages.get(userMessages.size() - 2).getContent().trim();
        }
        return lastMessage;
    }
}
