package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.SensitiveWordUtil;
import io.reactivex.Observable;

@Order(1)
@Component
public class SecurityFilterImpl implements BeforeModel, AfterModel {

    @Override
    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) return request;
        ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
        String raw = last.getContent();
        String filter = SensitiveWordUtil.filter(raw);
        last.setContent(filter);
        return request;
    }

    @Override
    public ChatCompletionResult apply(ChatCompletionResult result) {
        return SensitiveWordUtil.filter4ChatCompletionResult(result);
    }

    @Override
    public Observable<ChatCompletionResult> stream(Observable<ChatCompletionResult> source) {
        return source;
    }
}