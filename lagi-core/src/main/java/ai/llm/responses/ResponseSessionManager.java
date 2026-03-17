package ai.llm.responses;

import ai.common.ModelService;
import ai.common.utils.LRUCache;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.medusa.pojo.PromptInput;
import ai.medusa.pojo.PromptParameter;
import ai.medusa.utils.PromptInputUtil;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ToolCall;
import ai.openai.pojo.ToolCallFunction;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.hadoop.util.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResponseSessionManager {
    private static final int CACHE_SIZE = 1000;
    private static final long TTL_DAYS = 5L;
    private static final ResponseSessionManager INSTANCE = new ResponseSessionManager();
    private final LRUCache<String, ResponseSessionState> sessionCache =
            new LRUCache<>(CACHE_SIZE, TTL_DAYS, TimeUnit.DAYS);

    private final LRUCache<List<PromptInput>, ResponseSessionState> splitSessionCache =
            new LRUCache<>(CACHE_SIZE, TTL_DAYS, TimeUnit.DAYS);

    public static ResponseSessionManager getInstance() {
        return INSTANCE;
    }

    public ResponseSessionContext prepare(ai.openai.pojo.ChatCompletionRequest request, ModelService modelService) {
        ResponseSessionContext context = new ResponseSessionContext();
        context.setSessionId(request.getSessionId());
        context.setBackend(modelService.getBackend());
        context.setModel(request.getModel() == null ? modelService.getModel() : request.getModel());
        context.setProtocol(ResponseProtocolUtil.normalize(modelService.getProtocol()));

        SampleIntentServiceImpl sampleIntentService = new SampleIntentServiceImpl();

        List<ChatMessage> chatMessages = request.getMessages();
        List<ChatMessage> systemMessages = chatMessages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
        Integer validAssistantIndex = ChatCompletionUtil.findValidAssistantIndex(chatMessages);
        List<ChatMessage> incrementalMessages;
        List<ChatMessage> keyMessages;
        // first message
        if(validAssistantIndex == null) {
            Integer assistantIndex = ChatCompletionUtil.findAssistantIndex(chatMessages);
            if(assistantIndex != null) {
                incrementalMessages = chatMessages.subList(assistantIndex + 1, chatMessages.size());
                keyMessages = chatMessages.subList(0, assistantIndex + 1).stream().filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
                keyMessages = new ArrayList<>(keyMessages);
                return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
            } else {
                incrementalMessages = chatMessages.subList(0, chatMessages.size()).stream().filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
                context.setInputMessages(chatMessages);
                context.setNormalizedMessages(incrementalMessages);
                context.setStateful(false);
                return context;
            }
        }
        incrementalMessages = chatMessages.subList(validAssistantIndex + 1, chatMessages.size());
        List<ChatMessage> userMessages = incrementalMessages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_USER)).collect(Collectors.toList());
        List<ChatMessage> toolMessages = incrementalMessages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_TOOL)).collect(Collectors.toList());
        List<Integer> theFinalRoundOfConversation = sampleIntentService.theFinalRoundOfConversation(request.getMessages());
        if(userMessages.isEmpty()) {
            // 增长是 tool
            incrementalMessages = toolMessages;
            if(theFinalRoundOfConversation.isEmpty()) {
                keyMessages = chatMessages.subList(0, validAssistantIndex).stream()
                        .filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM))
                        .collect(Collectors.toList());
                return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
            } else {
                Integer conversationStartIndex = theFinalRoundOfConversation.get(0);
                keyMessages = chatMessages.subList(conversationStartIndex, validAssistantIndex + 1);
                return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
            }
        } else {
            // user
            incrementalMessages = userMessages;
            if(theFinalRoundOfConversation.isEmpty()) {
                keyMessages = chatMessages.subList(0, validAssistantIndex).stream()
                        .filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM))
                        .collect(Collectors.toList());
                return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
            } else{
                Integer conversationStartIndex = theFinalRoundOfConversation.get(0);
                keyMessages = chatMessages.subList(conversationStartIndex, validAssistantIndex + 1);
                ChatMessage chatMessage = userMessages.get(0);
                boolean aContinue = sampleIntentService.isContinue(keyMessages, chatMessage);
                if(aContinue) {
                    return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
                } else {
                    keyMessages = chatMessages.subList(theFinalRoundOfConversation.get(1), chatMessages.size() - 1);
                    return getResponseSessionContext(keyMessages, systemMessages, incrementalMessages, context);
                }
            }
        }
    }

    private List<PromptInput> convert2PromptInputs(List<ChatMessage> messages) {
        List<PromptInput> promptInputs =  new ArrayList<>();
        for (ChatMessage message : messages) {
            PromptInput promptInput = PromptInput.builder()
                    .promptList(Lists.newArrayList(message.getContent() == null ? "" : message.getContent()))
                    .parameter(PromptParameter.builder().role(message.getRole()).build())
                    .build();
            promptInputs.add(promptInput);
        }
        return promptInputs;
    }

    private ResponseSessionContext getResponseSessionContext(List<ChatMessage> keyMessages, List<ChatMessage> systemMessages, List<ChatMessage> incrementalMessages, ResponseSessionContext context) {
        ResponseSessionState responseSessionState = splitSessionCache.get(convert2PromptInputs(keyMessages));
        if(responseSessionState != null){
            systemMessages.addAll(incrementalMessages);
            context.setInputMessages(systemMessages);
            context.setNormalizedMessages(keyMessages);
//            context.setPreviousResponseId(responseSessionState.getPreviousResponseId());
            context.setStateful(true);
            return context;
        } else {
            // key + increment = normalized
            // system + normalized = input
            List<ChatMessage> normalized = new ArrayList<>(keyMessages);
            normalized.addAll(incrementalMessages);
            systemMessages.addAll(normalized);
            context.setInputMessages(systemMessages);
            context.setNormalizedMessages(normalized);
            context.setStateful(false);
            return context;
        }
    }

    public void onSuccess(ResponseSessionContext context, String responseId) {
        onSuccess(context, responseId, null);
    }

    public void onSuccess(ResponseSessionContext context, String responseId, ChatMessage responseMessage) {
        if (context == null  || StrUtil.isBlank(responseId)) {
            return;
        };
        ResponseSessionState responseSessionState = splitSessionCache.get(convert2PromptInputs(context.getNormalizedMessages()));
        if(responseSessionState != null) {
            List<ChatMessage> lastConversation = new ArrayList<>(context.getNormalizedMessages());
            lastConversation.add(responseMessage);
            splitSessionCache.put(convert2PromptInputs(lastConversation), responseSessionState);
            splitSessionCache.remove(convert2PromptInputs(context.getNormalizedMessages()));
        } else {
            ResponseSessionState state = new ResponseSessionState();
            state.setPreviousResponseId(responseId);
            state.setBackend(context.getBackend());
            state.setModel(context.getModel());
            state.setProtocol(context.getProtocol());
            List<ChatMessage> normalizeMessages = new ArrayList<>(context.getNormalizedMessages());
            normalizeMessages.add(responseMessage);
            splitSessionCache.put(convert2PromptInputs(normalizeMessages), state);
        }
    }

    private boolean isReusable(ResponseSessionState state, ResponseSessionContext context) {
        return state != null
                && StrUtil.isNotBlank(state.getPreviousResponseId())
                && StrUtil.equals(state.getBackend(), context.getBackend())
                && StrUtil.equals(state.getModel(), context.getModel())
                && StrUtil.equals(state.getProtocol(), context.getProtocol());
    }

    private List<ChatMessage> getIncrementalMessages(List<ChatMessage> previous, List<ChatMessage> current) {
        if (previous == null || current == null || current.size() <= previous.size()) {
            return null;
        }
        for (int i = 0; i < previous.size(); i++) {
            if (!previous.get(i).equals(current.get(i))) {
                return null;
            }
        }
        return normalizeMessages(current.subList(previous.size(), current.size()));
    }

    private List<ChatMessage> normalizeMessages(List<ChatMessage> messages) {
        List<ChatMessage> normalized = new ArrayList<>();
        if (messages == null) {
            return normalized;
        }
        for (ChatMessage message : messages) {
            ChatMessage copy = normalizeMessage(message);
            if (copy != null) {
                normalized.add(copy);
            }
        }
        return normalized;
    }

    private ChatMessage normalizeMessage(ChatMessage message) {
        if (message == null) {
            return null;
        }
        ChatMessage copy = new ChatMessage();
        copy.setRole(message.getRole());
        copy.setContent(message.getContent());
        copy.setReasoning_content(message.getReasoning_content());
        copy.setTool_call_id(message.getTool_call_id());
        if (message.getTool_calls() != null) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolCall toolCall : message.getTool_calls()) {
                ToolCall callCopy = new ToolCall();
                callCopy.setId(toolCall.getId());
                callCopy.setType(toolCall.getType());
                if (toolCall.getFunction() != null) {
                    ToolCallFunction function = new ToolCallFunction();
                    function.setName(toolCall.getFunction().getName());
                    function.setArguments(toolCall.getFunction().getArguments());
                    callCopy.setFunction(function);
                }
                calls.add(callCopy);
            }
            copy.setTool_calls(calls);
        }
        return copy;
    }
}
