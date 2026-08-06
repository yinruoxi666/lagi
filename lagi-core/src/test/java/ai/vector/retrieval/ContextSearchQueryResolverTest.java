package ai.vector.retrieval;

import ai.intent.enums.IntentStatusEnum;
import ai.intent.pojo.IntentResult;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.vector.pojo.HybridMetadataSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextSearchQueryResolverTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void usesLastMessageForIndependentQuestion() {
        ChatCompletionRequest request = request(
                message("user", "上一轮问题"),
                message("assistant", "上一轮回答"),
                message("user", "企业数据安全制度"));

        assertEquals("企业数据安全制度",
                ContextSearchQueryResolver.resolve(request, new IntentResult()));
    }

    @Test
    void combinesReferencedContextForContinuedQuestion() {
        ChatCompletionRequest request = request(
                message("user", "企业数据安全制度"),
                message("assistant", "制度包括访问控制和审计"),
                message("user", "它有哪些审计要求？"));
        IntentResult intent = new IntentResult();
        intent.setStatus(IntentStatusEnum.CONTINUE.getName());
        intent.setContinuedIndex(0);

        assertEquals("企业数据安全制度它有哪些审计要求？",
                ContextSearchQueryResolver.resolve(request, intent));
    }

    @Test
    void rejectsMissingContextWhenTextIsEmpty() {
        ChatCompletionRequest request = new ChatCompletionRequest();

        assertThrows(IllegalArgumentException.class,
                () -> ContextSearchQueryResolver.resolve(request, new IntentResult()));
    }

    @Test
    void acceptsSingularMessageAliasWithoutChangingCanonicalMessagesField() throws Exception {
        HybridMetadataSearchRequest request = OBJECT_MAPPER.readValue(
                "{\"message\":[{\"role\":\"user\",\"content\":\"审计要求\"}]}",
                HybridMetadataSearchRequest.class);

        assertEquals("审计要求", request.getMessages().get(0).getContent());
    }

    private static ChatCompletionRequest request(ChatMessage... messages) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(messages));
        return request;
    }

    private static ChatMessage message(String role, String content) {
        return ChatMessage.builder().role(role).content(content).build();
    }
}
