package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.ChatCompletionUtil;
import io.reactivex.Observable;

import java.util.List;

@Order(-1)
@Component
public class AgentFilterImpl implements BeforeModel, AfterModel {

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        List<ChatMessage> chatMessages = request.getMessages();

        List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
        List<ChatMessage> historyMessages = ChatCompletionUtil.getHistoryMessages(chatMessages);
        List<ChatMessage> incrementMessages = ChatCompletionUtil.getIncrementMessages(chatMessages);
        System.out.println("systemMessages:" + systemMessages);
        System.out.println("historyMessages:" + historyMessages);
        System.out.println("incrementMessages:" + incrementMessages);
        return request;
    }

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        return context.getResult();
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        return context.getStreamResult();
    }

}