package ai.llm.responses;

import ai.common.ModelService;
import ai.common.utils.LRUCache;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ToolCall;
import ai.openai.pojo.ToolCallFunction;
import ai.utils.LagiGlobal;
import cn.hutool.core.util.StrUtil;

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

    private final LRUCache<List<ChatMessage>, ResponseSessionState> splitSessionCache =
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
        List<Integer> theFinalRoundOfConversation = sampleIntentService.theFinalRoundOfConversation(request.getMessages());
        List<ChatMessage> chatMessages = request.getMessages();
        if(theFinalRoundOfConversation.isEmpty()) {
            // If the last round of messages is empty, meaning that all the messages represent an increase, then set the growth rate and return it.
            List<ChatMessage> incrementalMessages = chatMessages.stream().filter(message -> !message.getRole().equals("system")).collect(Collectors.toList());
            context.setInputMessages(incrementalMessages);
            context.setNormalizedMessages(incrementalMessages);
            context.setStateful(false);
            return context;
        } else {
            // If the last round of messages is not empty
            List<ChatMessage> lastConversation = chatMessages.subList(theFinalRoundOfConversation.get(0), theFinalRoundOfConversation.get(1));
            List<ChatMessage> incrementalMessages = chatMessages.subList(theFinalRoundOfConversation.get(1), chatMessages.size() - 1);
            long countUser = incrementalMessages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_USER)).count();

            if(countUser == 0) {
                // When there are no user messages and all are tools, it is assumed that it is continuing the previous conversation and the cache of the previous conversation is searched.
                ResponseSessionState responseSessionState = splitSessionCache.get(lastConversation);
                String previousResponseId = responseSessionState.getPreviousResponseId();
                context.setPreviousResponseId(previousResponseId);
                context.setNormalizedMessages(incrementalMessages);
                context.setInputMessages(incrementalMessages);
                context.setStateful(true);
                return context;
            } else {
                // Because the "tool" and "user" in the OpenAI rules are not consecutive, this part of the growth message must be from a single user. At this point, the "incrementalMessages" and "userMessages" are exactly the same.
                List<ChatMessage> userMessages = incrementalMessages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_USER)).collect(Collectors.toList());
                if(!userMessages.isEmpty()) {
                    boolean aContinue = sampleIntentService.isContinue(lastConversation, userMessages.get(0));
                    if(aContinue) {
                        // The user has continued the previous conversation. userMessages represents the increase in quantity. It is retrieved from the cache and previousResponseId is used.
                        ResponseSessionState responseSessionState = splitSessionCache.get(lastConversation);
                        String previousResponseId = responseSessionState.getPreviousResponseId();
                        context.setPreviousResponseId(previousResponseId);
                        context.setNormalizedMessages(incrementalMessages);
                        context.setInputMessages(incrementalMessages);
                        context.setStateful(true);
                        return context;
                    } else {
                        // The user's current conversation is not continuous with the previous one, and the userMessages variable does not show any growth.
                        context.setInputMessages(incrementalMessages);
                        context.setNormalizedMessages(incrementalMessages);
                        context.setStateful(false);
                        return context;
                    }
                }
            }
        }
        List<ChatMessage> incrementalMessages = chatMessages.stream().filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
        context.setInputMessages(incrementalMessages);
        context.setInputMessages(incrementalMessages);
        return context;
    }

    public void onSuccess(ResponseSessionContext context, String responseId) {
        onSuccess(context, responseId, null);
    }

    public void onSuccess(ResponseSessionContext context, String responseId, ChatMessage responseMessage) {
        if (context == null  || StrUtil.isBlank(responseId)) {
            return;
        };
        ResponseSessionState responseSessionState = splitSessionCache.get(context.getInputMessages());
        if(responseSessionState != null) {
            List<ChatMessage> lastConversation = new ArrayList<>(context.getInputMessages());
            lastConversation.add(responseMessage);
            splitSessionCache.put(lastConversation, responseSessionState);
            splitSessionCache.remove(context.getInputMessages());
        } else {
            ResponseSessionState state = new ResponseSessionState();
            state.setPreviousResponseId(responseId);
            state.setBackend(context.getBackend());
            state.setModel(context.getModel());
            state.setProtocol(context.getProtocol());
            splitSessionCache.put(context.getInputMessages(), state);
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
