package ai.llm.service;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.config.pojo.ModelFunction;
import ai.config.pojo.ModelFunctions;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LLMErrorConstants;
import ai.manager.AIManager;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import io.reactivex.Observable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompletionsServiceSafetyTest {

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
    void completionsWithSpecifiedModelShouldStopOnSafetyBlocked() {
        TestAIManager manager = new TestAIManager();
        TestAdapter blocked = TestAdapter.error("qwen-blocked", LLMErrorConstants.CONTENT_SAFETY_BLOCKED);
        manager.register(blocked.getModel(), blocked);

        CompletionsService service = new CompletionsService(manager);
        ChatCompletionRequest request = request("qwen-blocked", false);

        RRException exception = assertThrows(RRException.class, () -> service.completions(request));

        assertEquals(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, exception.getCode());
        assertEquals(1, blocked.completionsCalls.get());
    }

    @Test
    void streamWithSpecifiedModelShouldStopOnSafetyBlocked() {
        TestAIManager manager = new TestAIManager();
        TestAdapter blocked = TestAdapter.streamError("qwen-blocked", LLMErrorConstants.CONTENT_SAFETY_BLOCKED).priority(200);
        TestAdapter fallback = TestAdapter.streamSuccess("chatgpt-fallback", "fallback").priority(100);
        manager.register(blocked.getModel(), blocked);
        manager.register(fallback.getModel(), fallback);

        CompletionsService service = new CompletionsService(manager);
        ChatCompletionRequest request = request("qwen-blocked", true);

        RRException exception = assertThrows(RRException.class, () -> service.streamCompletions(request));

        assertEquals(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, exception.getCode());
        assertEquals(1, blocked.streamCalls.get());
        assertEquals(0, fallback.streamCalls.get());
    }

    @Test
    void streamWithSpecifiedModelShouldFallbackOnOrdinaryError() {
        TestAIManager manager = new TestAIManager();
        TestAdapter primary = TestAdapter.streamError("qwen-error", LLMErrorConstants.OTHER_ERROR).priority(200);
        TestAdapter fallback = TestAdapter.streamSuccess("chatgpt-fallback", "fallback").priority(100);
        manager.register(primary.getModel(), primary);
        manager.register(fallback.getModel(), fallback);

        CompletionsService service = new CompletionsService(manager);
        ChatCompletionRequest request = request("qwen-error", true);

        ChatCompletionResult result = service.streamCompletions(request).blockingFirst();

        assertEquals("fallback", result.getChoices().get(0).getMessage().getContent());
        assertEquals(1, primary.streamCalls.get());
        assertEquals(1, fallback.streamCalls.get());
    }

    @Test
    void streamWithEmptyModelShouldStopOnSafetyBlocked() {
        TestAIManager manager = new TestAIManager();
        TestAdapter blocked = TestAdapter.streamError("qwen-blocked", LLMErrorConstants.CONTENT_SAFETY_BLOCKED).priority(200);
        TestAdapter fallback = TestAdapter.streamSuccess("chatgpt-fallback", "fallback").priority(100);
        manager.register(blocked.getModel(), blocked);
        manager.register(fallback.getModel(), fallback);

        CompletionsService service = new CompletionsService(manager);
        ChatCompletionRequest request = request(null, true);

        RRException exception = assertThrows(RRException.class, () -> service.streamCompletions(request));

        assertEquals(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, exception.getCode());
        assertEquals(1, blocked.streamCalls.get());
        assertEquals(0, fallback.streamCalls.get());
    }

    @Test
    void streamWithEmptyModelShouldFallbackOnOrdinaryError() {
        TestAIManager manager = new TestAIManager();
        TestAdapter primary = TestAdapter.streamError("qwen-error", LLMErrorConstants.OTHER_ERROR).priority(200);
        TestAdapter fallback = TestAdapter.streamSuccess("chatgpt-fallback", "fallback").priority(100);
        manager.register(primary.getModel(), primary);
        manager.register(fallback.getModel(), fallback);

        CompletionsService service = new CompletionsService(manager);
        ChatCompletionRequest request = request(null, true);

        ChatCompletionResult result = service.streamCompletions(request).blockingFirst();

        assertEquals("fallback", result.getChoices().get(0).getMessage().getContent());
        assertEquals(1, primary.streamCalls.get());
        assertEquals(1, fallback.streamCalls.get());
    }

    private static ChatCompletionRequest request(String model, boolean stream) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setStream(stream);
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
        choice.setDelta(message);
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static class TestAIManager extends AIManager<ILlmAdapter> {
    }

    private static class TestAdapter extends ModelService implements ILlmAdapter {
        private final AtomicInteger completionsCalls = new AtomicInteger();
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final RRException completionException;
        private final RRException streamException;
        private final ChatCompletionResult completionResult;
        private final ChatCompletionResult streamResult;

        private TestAdapter(String model, RRException completionException, RRException streamException,
                            ChatCompletionResult completionResult, ChatCompletionResult streamResult) {
            this.model = model;
            this.backend = model;
            this.enable = true;
            this.priority = 100;
            this.completionException = completionException;
            this.streamException = streamException;
            this.completionResult = completionResult;
            this.streamResult = streamResult;
        }

        static TestAdapter error(String model, Integer code) {
            return new TestAdapter(model, new RRException(code, "error"), null, null, null);
        }

        static TestAdapter streamError(String model, Integer code) {
            return new TestAdapter(model, null, new RRException(code, "error"), null, null);
        }

        static TestAdapter streamSuccess(String model, String text) {
            return new TestAdapter(model, null, null, null, result(text));
        }

        TestAdapter priority(int priority) {
            this.priority = priority;
            return this;
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
        public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
            streamCalls.incrementAndGet();
            if (streamException != null) {
                throw streamException;
            }
            return Observable.just(streamResult);
        }
    }
}
