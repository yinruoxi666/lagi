package ai.llm.service;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.config.pojo.ModelFunction;
import ai.config.pojo.ModelFunctions;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import io.reactivex.Observable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmRouteServiceSafetyTest {

    @BeforeAll
    static void initContext() {
        ModelFunction chat = new ModelFunction(false, null, 3600, Integer.MAX_VALUE, 4096);
        ModelFunctions functions = new ModelFunctions(null, chat, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        GlobalConfigurations globalConfigurations = new GlobalConfigurations();
        globalConfigurations.setFunctions(functions);
        ContextLoader.configuration = globalConfigurations;
    }

    @Test
    void failoverWithEmptyModelShouldStopOnSafetyBlocked() {
        TestAdapter blocked = TestAdapter.error("qwen-blocked", LLMErrorConstants.CONTENT_SAFETY_BLOCKED);
        TestAdapter fallback = TestAdapter.success("chatgpt-fallback", "fallback");

        LlmRouteService service = new LlmRouteService();
        ChatCompletionRequest request = request();

        RRException exception = assertThrows(RRException.class,
                () -> service.failoverGetChatCompletionResult(request, Arrays.asList(blocked, fallback)));

        assertEquals(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, exception.getCode());
        assertEquals(1, blocked.completionsCalls.get());
        assertEquals(0, fallback.completionsCalls.get());
    }

    @Test
    void failoverWithEmptyModelShouldFallbackOnOrdinaryError() {
        TestAdapter primary = TestAdapter.error("qwen-error", LLMErrorConstants.OTHER_ERROR);
        TestAdapter fallback = TestAdapter.success("chatgpt-fallback", "fallback");

        LlmRouteService service = new LlmRouteService();
        ChatCompletionRequest request = request();

        ChatCompletionResult result = service.failoverGetChatCompletionResult(request, Arrays.asList(primary, fallback));

        assertEquals("fallback", result.getChoices().get(0).getMessage().getContent());
        assertEquals(1, primary.completionsCalls.get());
        assertEquals(1, fallback.completionsCalls.get());
    }

    private static ChatCompletionRequest request() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Collections.singletonList(ChatMessage.builder().role("user").content("test").build()));
        return request;
    }

    private static ChatCompletionResult result(String text) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(text);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        choice.setMessage(message);
        ChatCompletionChoice deltaChoice = choice;
        deltaChoice.setDelta(message);
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static class TestAdapter extends ModelService implements ILlmAdapter {
        private final AtomicInteger completionsCalls = new AtomicInteger();
        private final RRException completionException;
        private final ChatCompletionResult completionResult;

        private TestAdapter(String model, RRException completionException, ChatCompletionResult completionResult) {
            this.model = model;
            this.backend = model;
            this.enable = true;
            this.priority = 100;
            this.completionException = completionException;
            this.completionResult = completionResult;
        }

        static TestAdapter error(String model, Integer code) {
            return new TestAdapter(model, new RRException(code, "error"), null);
        }

        static TestAdapter success(String model, String text) {
            return new TestAdapter(model, null, result(text));
        }

        @Override
        public ChatCompletionResult completions(ChatCompletionRequest request) {
            completionsCalls.incrementAndGet();
            if (completionException != null) {
                throw completionException;
            }
            return completionResult;
        }

        @Override
        public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
            throw new UnsupportedOperationException();
        }
    }
}
