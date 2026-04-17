package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.common.utils.MappingIterable;
import ai.llm.utils.convert.QwenConvert;
import ai.openai.pojo.*;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.azure.storage.common.policy.ScrubEtagPolicy;
import com.google.gson.Gson;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@LLM(modelNames = {"qwen-turbo","qwen-plus","qwen-max","qwen-max-1201","qwen-max-longcontext","qwen3-max"})
public class QwenAdapter extends ModelService implements ILlmAdapter {
    private Set<String> multiModalModels;

    {{
        multiModalModels = new HashSet<>();
        multiModalModels.add("qwen3.6-plus");
        multiModalModels.add("qwen3.5-397b-a17b");
        multiModalModels.add("qwen3.5-122b-a10b");

    }
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {

        try {

            if (StrUtil.isNotBlank(chatCompletionRequest.getModel()) && multiModalModels.contains(chatCompletionRequest.getModel())) {
                MultiModalConversation conversation = new MultiModalConversation();
                MultiModalConversationParam conversationParam = convertMultiModalConversationRequest(chatCompletionRequest);
                MultiModalConversationResult conversationResult ;
                conversationResult = conversation.call(conversationParam);
                return convertResponse(conversationResult);
            } else {
                Generation gen = new Generation();
                GenerationParam param = convertRequest(chatCompletionRequest);
                GenerationResult result;
                result = gen.call(param);
                return convertResponse(result);
            }


        } catch (Exception e) {
            RRException exception = QwenConvert.convert2RRexception(e);
            log.error("qwen  api code {} error {}", exception.getCode(), exception.getMsg());
            throw new RRException(exception.getCode(), exception.getMsg());
        }
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        long startNs = System.nanoTime();
        String model = Optional.ofNullable(chatCompletionRequest.getModel()).orElse(getModel());
        int messageCount = chatCompletionRequest.getMessages() == null ? 0 : chatCompletionRequest.getMessages().size();
        log.info("[QWEN_STREAM_START] model={} messages={}", model, messageCount);
        if (StrUtil.isNotBlank(chatCompletionRequest.getModel()) && multiModalModels.contains(chatCompletionRequest.getModel())) {

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationParam conversationParam =convertMultiModalConversationRequest(chatCompletionRequest);

            Flowable<MultiModalConversationResult> result = null;
            try {
                result = conv.streamCall(conversationParam);
            } catch (UploadFileException | NoApiKeyException e) {
                throw new RuntimeException(e);
            }
            Iterable<MultiModalConversationResult> resultIterable = result.blockingIterable();
            java.util.Iterator<MultiModalConversationResult> iterator = resultIterable.iterator();
            String requestId = null;
            ChatCompletionResult firstChunk = null;
            try {
                if (iterator.hasNext()) {
                    MultiModalConversationResult firstResult = iterator.next();
                    requestId = firstResult.getRequestId();
                    firstChunk = convertResponse(firstResult);
                    long firstChunkMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                    log.info("[QWEN_STREAM_FIRST_CHUNK] requestId={} model={} firstChunkMs={}", requestId, model, firstChunkMs);
                }
            } catch (ApiException e) {
                RRException exception = QwenConvert.convert2RRexception(e);
                log.error("qwen  stream  api code {} error {}", exception.getCode(), exception.getMsg());
                throw exception;
            }

            Iterable<MultiModalConversationResult> singleUseIterable = () -> iterator;
            Iterable<ChatCompletionResult> iterable = new MappingIterable<>(singleUseIterable, this::convertResponse);

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Throwable> terminalError = new AtomicReference<>();

            Observable<ChatCompletionResult> tail = Observable.fromIterable(iterable);
            Observable<ChatCompletionResult> stream = firstChunk == null ? tail : Observable.just(firstChunk).concatWith(tail);

            String finalRequestId = requestId;
            return stream
                    .doOnComplete(() -> completed.set(true))
                    .doOnError(terminalError::set)
                    .doFinally(() -> {
                        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                        Throwable error = terminalError.get();
                        String status = completed.get() ? "complete" : (error == null ? "disposed" : "error");
                        log.info("[QWEN_STREAM_TOTAL] requestId={} model={} totalMs={} status={}", finalRequestId, model, totalMs, status);
                    });
        } else {
            Generation gen = new Generation();
            GenerationParam param = convertRequest(chatCompletionRequest);
            Flowable<GenerationResult> result = null;
            try {
                result = gen.streamCall(param);
            } catch (NoApiKeyException | InputRequiredException e) {
                throw new RuntimeException(e);
            }

            Iterable<GenerationResult> resultIterable = result.blockingIterable();
            java.util.Iterator<GenerationResult> iterator = resultIterable.iterator();

            String requestId = null;
            ChatCompletionResult firstChunk = null;
            try {
                if (iterator.hasNext()) {
                    GenerationResult firstResult = iterator.next();
                    requestId = firstResult.getRequestId();
                    firstChunk = convertResponse(firstResult);
                    long firstChunkMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                    log.info("[QWEN_STREAM_FIRST_CHUNK] requestId={} model={} firstChunkMs={}", requestId, model, firstChunkMs);
                }
            } catch (ApiException e) {
                RRException exception = QwenConvert.convert2RRexception(e);
                log.error("qwen  stream  api code {} error {}", exception.getCode(), exception.getMsg());
                throw exception;
            }

            Iterable<GenerationResult> singleUseIterable = () -> iterator;
            Iterable<ChatCompletionResult> iterable = new MappingIterable<>(singleUseIterable, this::convertResponse);

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Throwable> terminalError = new AtomicReference<>();

            Observable<ChatCompletionResult> tail = Observable.fromIterable(iterable);
            Observable<ChatCompletionResult> stream = firstChunk == null ? tail : Observable.just(firstChunk).concatWith(tail);

            String finalRequestId = requestId;
            return stream
                    .doOnComplete(() -> completed.set(true))
                    .doOnError(terminalError::set)
                    .doFinally(() -> {
                        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                        Throwable error = terminalError.get();
                        String status = completed.get() ? "complete" : (error == null ? "disposed" : "error");
                        log.info("[QWEN_STREAM_TOTAL] requestId={} model={} totalMs={} status={}", finalRequestId, model, totalMs, status);
                    });
        }

    }

    private GenerationParam convertRequest(ChatCompletionRequest request) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            List<ToolCall> toolCalls = chatMessage.getTool_calls();
            List<ToolCallBase> collect = null;
            if(toolCalls != null) {
                collect = toolCalls.stream().map(toolCall -> {
//                    com.alibaba.dashscope.tools.ToolCallFunction build = com.alibaba.dashscope.tools.ToolCallFunction.builder().build();
                    com.alibaba.dashscope.tools.ToolCallFunction build = new ToolCallFunction();
                    BeanUtil.copyProperties(toolCall, build);
                    return build;
                }).collect(Collectors.toList());
            }

            Message msg = Message.builder()
                    .role(chatMessage.getRole())
                    .content(chatMessage.getContent())
                    .toolCallId(chatMessage.getTool_call_id())
                    .toolCalls(collect)
                    .build();
            messages.add(msg);
        }

        boolean stream = Optional.ofNullable(request.getStream()).orElse(false);
        String model = Optional.ofNullable(request.getModel()).orElse(getModel());
        Boolean enableThinking = false;

        int maxTokens = request.getMax_tokens();
        if (request.getMax_tokens() >= 2000) {
            maxTokens = 2000;
        }

        List<Tool> tools = request.getTools();
        List<ToolBase> toolFunctions = null;
        if(tools != null) {
            toolFunctions = tools.stream().map(tool -> {
                String json = new Gson().toJson(tool);
                return new Gson().fromJson(json, ToolFunction.class);
            }).collect(Collectors.toList());
        }
        return GenerationParam.builder()
                .apiKey(getApiKey())
                .model(model)
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .maxTokens(maxTokens)
                .temperature((float) request.getTemperature())
                .enableSearch(stream)
                .enableThinking(enableThinking)
                .incrementalOutput(stream)
                .tools(toolFunctions)
                .build();
    }

    private MultiModalConversationParam convertMultiModalConversationRequest(ChatCompletionRequest request) {
        List<MultiModalMessage> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            List<ToolCall> toolCalls = chatMessage.getTool_calls();
            List<ToolCallBase> collect = null;
            if(toolCalls != null) {
                collect = toolCalls.stream().map(toolCall -> {
//                    com.alibaba.dashscope.tools.ToolCallFunction build = com.alibaba.dashscope.tools.ToolCallFunction.builder().build();
                    com.alibaba.dashscope.tools.ToolCallFunction build = new ToolCallFunction();
                    BeanUtil.copyProperties(toolCall, build);
                    return build;
                }).collect(Collectors.toList());
            }

            MultiModalMessage msg = MultiModalMessage.builder()
                    .role(chatMessage.getRole())
                    .content(Collections.singletonList(Collections.singletonMap("text", chatMessage.getContent())))
                    .toolCallId(chatMessage.getTool_call_id())
                    .toolCalls(collect)
                    .build();
            messages.add(msg);
        }

        boolean stream = Optional.ofNullable(request.getStream()).orElse(false);
        String model = Optional.ofNullable(request.getModel()).orElse(getModel());
        Boolean enableThinking = false;

        int maxTokens = request.getMax_tokens();
        if (request.getMax_tokens() >= 2000) {
            maxTokens = 2000;
        }

        List<Tool> tools = request.getTools();
        List<ToolBase> toolFunctions = null;
        String toolChoice = "none";
        if(tools != null) {
            toolFunctions = tools.stream().map(tool -> {
                String json = new Gson().toJson(tool);
                return new Gson().fromJson(json, ToolFunction.class);
            }).collect(Collectors.toList());
            toolChoice = StrUtil.isNotBlank(request.getTool_choice()) ? request.getTool_choice() : "none";
        }
        return MultiModalConversationParam.builder()
                .apiKey(getApiKey())
                .model(model)
                .messages(messages)
                .maxTokens(maxTokens)
                .temperature((float) request.getTemperature())
                .enableSearch(stream)
                .enableThinking(enableThinking)
                .incrementalOutput(stream)
                .tools(toolFunctions)
                .toolChoice("required")
                .build();
    }

    private ChatCompletionResult convertResponse(GenerationResult response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(response.getRequestId());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        Message message = response.getOutput().getChoices().get(0).getMessage();
        chatMessage.setContent(message.getContent());
        chatMessage.setRole("assistant");
        List<ToolCallBase> toolCalls = message.getToolCalls();
        List<ToolCall> toolCallList = new ArrayList<>();
        if(toolCalls != null) {
            toolCallList = toolCalls.stream().map(toolCall -> {
                String json = new Gson().toJson(toolCall);
                return new Gson().fromJson(json, ToolCall.class);
            }).collect(Collectors.toList());
        }
        chatMessage.setTool_calls(toolCallList);
        choice.setMessage(chatMessage);
        choice.setFinish_reason(response.getOutput().getFinishReason());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }
    private ChatCompletionResult convertResponse(MultiModalConversationResult response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(response.getRequestId());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        MultiModalMessage message = response.getOutput().getChoices().get(0).getMessage();
        if (CollUtil.isNotEmpty(message.getContent())) {
            chatMessage.setContent(message.getContent().get(0).get("text").toString());
        }

        chatMessage.setRole("assistant");
        List<ToolCallBase> toolCalls = message.getToolCalls();
        List<ToolCall> toolCallList = new ArrayList<>();
        if(toolCalls != null) {
            toolCallList = toolCalls.stream().map(toolCall -> {
                String json = new Gson().toJson(toolCall);
                return new Gson().fromJson(json, ToolCall.class);
            }).collect(Collectors.toList());
        }
        chatMessage.setTool_calls(toolCallList);
        choice.setMessage(chatMessage);
        choice.setFinish_reason(response.getOutput().getFinishReason());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }
}
