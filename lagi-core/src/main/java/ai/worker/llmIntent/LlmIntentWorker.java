package ai.worker.llmIntent;

import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.MoonshotAdapter;
import ai.llm.service.CompletionsService;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LlmIntentWorker {

    public ChatCompletionResult process(ChatCompletionRequest chatCompletionRequest, String url) {
        ChatCompletionResult chatCompletionResult = null;
        // 将提示词整合进请求
        String system_prompt = "\"\"\"\n" +
//                "你是一个机场的客服人员，你负责判断用户提出的各种问题。请判断用户的提出的问题是否有关：询问天气、包含什么城市、是否询问航班状态、包含的航班号是多少。你的回答是是否询问天气(is_ask_weather),包含什么城市(city),是否询问航班状态(is_ask_flight_status),包含什么航班号(flight_no)\n\n" +
                "你是一个机场的客服人员，你负责判断用户提出的各种问题。请判断用户的提出的问题是否有关：询问天气、是否询问航班状态。你的回答是是否询问天气(is_ask_weather),是否询问航班状态(is_ask_flight_status)\n\n" +
                "请使用如下 JSON 格式输出你的回复：\n\n" +
                "{\n" +
                "\"is_ask_weather\": \"是否询问天气\"," +
//                "\"city\": \"城市\"," +
                "\"is_ask_flight_status\": \"是否询问航班状态\"" +
//                "\"flight_no\": \"航班号\"" +
                "}\n\n" +
                "\"\"\"";
        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(system_prompt);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message);
        messages.addAll(chatCompletionRequest.getMessages());
        chatCompletionRequest.setMessages(messages);
        chatCompletionRequest.setModel("moonshot-v1-8k");
        MoonshotAdapter moonshotAdapter = (MoonshotAdapter)LlmManager.getInstance().getAdapter("moonshot-v1-8k");
//        chatCompletionResult = moonshotAdapter.intentCompletions(chatCompletionRequest);
        return chatCompletionResult;
    }
}
