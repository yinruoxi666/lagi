package ai.llm.utils;

import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.openai.pojo.*;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorStoreService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class CompletionUtil {
    private static final Gson gson = new Gson();
    private static final VectorStoreService vectorStoreService = new VectorStoreService();
    private static final int MAX_INPUT = ContextLoader.configuration.getFunctions().getChat().getContextLength();

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
//    private static final int MAX_INPUT = 1024;

    public static ChatCompletionResult getDummyCompletion() {
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        String currentDatetime = dateFormat.format(new Date());
        String message = currentDatetime + " " + UUID.randomUUID();
        String json = "{\"created\":1719495617,\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
                "\"content\":\"" + message + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":38," +
                "\"completion_tokens\":8,\"total_tokens\":46}}\n";
        return gson.fromJson(json, ChatCompletionResult.class);
    }

    public static void populateContext(ChatCompletionResult result, List<IndexSearchData> indexSearchDataList, String context) {
        if (result != null && !result.getChoices().isEmpty()
                && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
            IndexSearchData indexData = indexSearchDataList.get(0);
            List<String> imageList = vectorStoreService.getImageFiles(indexData);
            for (int i = 0; i < result.getChoices().size(); i++) {
                ChatMessage message = result.getChoices().get(i).getMessage();
                message.setContext(context);
                if (!(indexData.getFilename() != null && indexData.getFilename().size() == 1
                        && indexData.getFilename().get(0).isEmpty())) {
                    message.setFilename(indexData.getFilename());
                }
                message.setFilepath(indexData.getFilepath());
                message.setImageList(imageList);
            }
        }
    }

    public static String truncate(String context) {
        return truncate(context, MAX_INPUT);
    }

    public static String truncate(String context, int maxLength) {
        if (context == null) {
            return "";
        }
        if (context.length() <= maxLength) {
            return context;
        }
        return context.substring(0, maxLength);
    }

    public static List<ChatMessage> truncateChatMessages(List<ChatMessage> chatMessages) {
        return truncateChatMessages(chatMessages, MAX_INPUT);
    }

    public static List<ChatMessage> truncateChatMessages(List<ChatMessage> chatMessages, int maxLength) {
        if (chatMessages != null && !chatMessages.isEmpty()) {
            ChatMessage systemChatMessage = null;
            if (chatMessages.get(0).getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)) {
                systemChatMessage = chatMessages.get(0);
            }
            ChatMessage lastQuestion = chatMessages.get(chatMessages.size() - 1);
            int userMaxLength = maxLength;
            if (systemChatMessage != null) {
                userMaxLength = maxLength - systemChatMessage.getContent().length();
            }
            lastQuestion.setContent(truncate(lastQuestion.getContent(), userMaxLength));
            int length = lastQuestion.getContent().length();
            int lastIndex = chatMessages.size() - 1;
            for (int i = chatMessages.size() - 2; i >= 0; i--) {
                ChatMessage chatMessage = chatMessages.get(i);
                length += chatMessage.getContent().length();
                if (length > userMaxLength) {
                    break;
                }
                if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_USER)) {
                    lastIndex = i;
                }
            }
            chatMessages = chatMessages.subList(lastIndex, chatMessages.size());
            if (systemChatMessage != null) {
                chatMessages.add(0, systemChatMessage);
            }
        }
        return chatMessages;
    }

    public static boolean isMultiModal(String content) {
        try {
            List<MultiModalContent> contentList = mapper.readValue(content, new TypeReference<List<MultiModalContent>>() {
            });
            if (!contentList.isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isMultiModal(ChatCompletionRequest chatCompletionRequest) {
        String content = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        return isMultiModal(content);
    }

    public static void main(String[] args) {
//        ChatCompletionResult result = getDummyCompletion();
//        System.out.println(result.getChoices().get(0).getMessage().getContent());
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message1 = new ChatMessage();
        message1.setRole(LagiGlobal.LLM_ROLE_USER);
        message1.setContent("十四号登机口怎么走");
        messages.add(message1);
        ChatMessage message2 = new ChatMessage();
        message2.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
        message2.setContent("从这里直走，经过安检后，左转就能看到十四号登机口。");
        List<ToolCall> toolCallList = new ArrayList<>();
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_kdtyggPXh6NjuYS2LPU86hFD");
        toolCall.setType("function");
        ToolCallFunction function = new ToolCallFunction();
        function.setName("get_position");
        function.setArguments(
                "{\"place\":\"十四号登机口\",\"category\":\"登机口\"}"
        );
        toolCall.setFunction(function);
        toolCallList.add(toolCall);
        message2.setTool_calls(toolCallList);
        messages.add(message2);
        ChatMessage message3 = new ChatMessage();
        message3.setRole("tool");
        message3.setContent("十四号登机口距离您500米，步行大约8分钟");
        message3.setTool_call_id("call_kdtyggPXh6NjuYS2LPU86hFD");
        messages.add(message3);
        ChatMessage message4 = new ChatMessage();
        message4.setRole(LagiGlobal.LLM_ROLE_USER);
        message4.setContent("星巴克怎么走");
        truncateChatMessages(messages);
    }
}
