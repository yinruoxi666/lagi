package ai.agent.chat.lydaas;

import ai.agent.chat.BaseChatAgent;
import ai.agent.chat.lydaas.pojo.LydaasChatDataItem;
import ai.agent.chat.lydaas.pojo.LydaasChatRequest;
import ai.agent.chat.lydaas.pojo.LydaasChatContent;
import ai.agent.chat.lydaas.pojo.LydaasChatResponse;
import ai.agent.chat.lydaas.pojo.LydaasThoughtChainContentInfo;
import ai.agent.chat.lydaas.pojo.LydaasThoughtChainNode;
import ai.common.utils.LRUCache;
import ai.common.exception.RRException;
import ai.config.pojo.AgentConfig;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PekIcdhAgent extends BaseChatAgent {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_BASE_URL = "https://gateway.lydaas.com";
    private static final String CHAT_PATH = "/quick/agent/chat/sse";
    private static final String CHAT_STATUS_END = "CHAT_STATUS_END";
    private static final String CHAT_STATUS_ERROR = "CHAT_STATUS_ERROR";
    private static final String THOUGHT_STATUS_FAIL = "fail";
    private static final String MSG_TYPE_AGENT_CHAT_MESSAGE = "AGENT_CHAT_MESSAGE";
    private static final String BIZ_INVOKE_FROM = "ROBOT";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int CACHE_SIZE = 1000;
    private static final int CACHE_TTL_DAYS = 7;
    private static final LRUCache<String, String> SESSION_CONVERSATION_CACHE =
            new LRUCache<>(CACHE_SIZE, CACHE_TTL_DAYS, TimeUnit.DAYS);
    private static final LRUCache<String, List<LydaasChatDataItem>> CONVERSATION_HISTORY_CACHE =
            new LRUCache<>(CACHE_SIZE, CACHE_TTL_DAYS, TimeUnit.DAYS);

    public PekIcdhAgent(AgentConfig agentConfig) {
        super(agentConfig);
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        AtomicReference<String> finalContent = new AtomicReference<>("");
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<LydaasChatResponse> lastResponseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Disposable disposable = streamResponses(data).subscribe(response -> {
            lastResponseRef.set(response);
            String effectiveText = extractEffectiveText(response);
            if (effectiveText != null) {
                finalContent.set(effectiveText);
            }
        }, throwable -> {
            errorRef.set(throwable);
            latch.countDown();
        }, latch::countDown);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disposable.dispose();
            throw new RRException(LLMErrorConstants.OTHER_ERROR, "lydaas agent interrupted");
        }
        if (!disposable.isDisposed()) {
            disposable.dispose();
        }
        Throwable throwable = errorRef.get();
        if (throwable != null) {
            if (throwable instanceof RRException) {
                throw (RRException) throwable;
            }
            throw new RRException(LLMErrorConstants.OTHER_ERROR, throwable.getMessage());
        }
        LydaasChatResponse lastResponse = lastResponseRef.get();
        if (!isBusinessError(lastResponse)) {
            saveConversationContext(data == null ? null : data.getSessionId(), lastResponse);
        }
        if (isBusinessError(lastResponse)) {
            return toChatCompletionResult(finalContent.get(), null, extractBusinessErrorTitle(lastResponse));
        }
        return toChatCompletionResult(finalContent.get(), null, "stop");
    }

    public Observable<ChatCompletionResult> streamCommunicate(ChatCompletionRequest request) {
        return Observable.create(emitter -> {
            AtomicReference<String> previousFullText = new AtomicReference<>("");
            AtomicReference<String> previousTextMessage = new AtomicReference<>("");
            AtomicReference<LydaasChatResponse> lastResponseRef = new AtomicReference<>();
            String query = extractQuery(request);
            log.info("[pek_icdh][streamCommunicate] start, sessionId={}, query={}, stream={}",
                    request == null ? null : request.getSessionId(),
                    preview(query),
                    request == null ? null : request.getStream());
            Disposable disposable = streamResponses(request).subscribe(response -> {
                lastResponseRef.set(response);
                if (isBusinessError(response)) {
                    String finishReason = extractBusinessErrorTitle(response);
                    log.info("[pek_icdh][streamCommunicate] emit business error terminal, msgId={}, finishReason={}",
                            response.getMsgId(), preview(finishReason));
                    if (!emitter.isDisposed()) {
                        emitter.onNext(toChatCompletionResult("", null, finishReason));
                    }
                    return;
                }
                String currentFullText = extractDocAskContent(response);
                String currentTextMessage = extractContentText(response);
                log.info("[pek_icdh][streamCommunicate] response, msgId={}, chatStatus={}, isEnd={}, docAskContent={}, contentText={}, previousFullText={}, previousTextMessage={}",
                        response == null ? null : response.getMsgId(),
                        response == null ? null : response.getChatStatus(),
                        response == null ? null : response.getIsEnd(),
                        preview(currentFullText),
                        preview(currentTextMessage),
                        preview(previousFullText.get()),
                        preview(previousTextMessage.get()));
                if (StrUtil.isNotBlank(currentFullText)) {
                    String delta = computeDelta(previousFullText.get(), currentFullText);
                    previousFullText.set(currentFullText);
                    if (StrUtil.isNotEmpty(delta) && !emitter.isDisposed()) {
                        log.info("[pek_icdh][streamCommunicate] emit docAskContent delta, msgId={}, delta={}, deltaLength={}, fullLength={}",
                                response == null ? null : response.getMsgId(),
                                preview(delta),
                                delta.length(),
                                currentFullText.length());
                        emitter.onNext(toChatCompletionResult(delta, null, "stop"));
                    } else {
                        log.info("[pek_icdh][streamCommunicate] skip empty docAskContent delta, msgId={}, currentText={}",
                                response == null ? null : response.getMsgId(),
                                preview(currentFullText));
                    }
                    return;
                }
                if (StrUtil.isBlank(currentTextMessage)) {
                    log.info("[pek_icdh][streamCommunicate] skip blank effective text, msgId={}",
                            response == null ? null : response.getMsgId());
                    return;
                }
                if (currentTextMessage.equals(previousTextMessage.get())) {
                    log.info("[pek_icdh][streamCommunicate] skip duplicate content.text, msgId={}, text={}",
                            response == null ? null : response.getMsgId(),
                            preview(currentTextMessage));
                    return;
                }
                previousTextMessage.set(currentTextMessage);
                if (!emitter.isDisposed()) {
                    log.info("[pek_icdh][streamCommunicate] emit content.text, msgId={}, text={}, length={}",
                            response == null ? null : response.getMsgId(),
                            preview(currentTextMessage),
                            currentTextMessage.length());
                    emitter.onNext(toChatCompletionResult(currentTextMessage, null, "stop"));
                }
            }, throwable -> {
                log.error("[pek_icdh][streamCommunicate] error, sessionId={}, message={}",
                        request == null ? null : request.getSessionId(),
                        throwable == null ? null : throwable.getMessage(), throwable);
                if (!emitter.isDisposed()) {
                    emitter.onError(throwable);
                }
            }, () -> {
                LydaasChatResponse lastResponse = lastResponseRef.get();
                if (!isBusinessError(lastResponse)) {
                    saveConversationContext(request == null ? null : request.getSessionId(), lastResponse);
                }
                log.info("[pek_icdh][streamCommunicate] complete, sessionId={}, finalText={}, finalStatus={}",
                        request == null ? null : request.getSessionId(),
                        preview(StrUtil.isNotBlank(previousFullText.get()) ? previousFullText.get() : previousTextMessage.get()),
                        lastResponse == null ? null : lastResponse.getChatStatus());
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            });
            emitter.setCancellable(() -> {
                log.info("[pek_icdh][streamCommunicate] cancelled, sessionId={}",
                        request == null ? null : request.getSessionId());
                disposable.dispose();
            });
        });
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 0 ? args[0] : "stream";
        System.out.println("mode: " + mode);
        String query = args.length > 1 ? args[1] : System.getenv("LYDAAS_QUERY");
        if (StrUtil.isBlank(query)) {
            query = "你好";
        }

        AgentConfig config = AgentConfig.builder()
                .name("pek-icdh")
                .token("Basic Tno1eUVFeFcyZFVFZ2E0NE12Z052dVJSQVJRNkNnMmFHR1FCUDl3OXFZNzpDSGZtaHMzZlRFSlhKWHd4bzNQZlptM2lzQ3FNQzI0aw==")
                .appId("81a337cc-6f7e-4719-8aa6-0379edadf97e")
                .userId("25081917363309")
                .endpoint(StrUtil.blankToDefault(System.getenv("LYDAAS_ENDPOINT"), DEFAULT_BASE_URL))
                .build();

        PekIcdhAgent agent = new PekIcdhAgent(config);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setSessionId("pek-icdh-main");
        request.setMessages(Collections.singletonList(ChatMessage.builder()
                .role("user")
                .content(query)
                .build()));

        if ("sync".equalsIgnoreCase(mode) || "non-stream".equalsIgnoreCase(mode)) {
            ChatCompletionResult result = agent.communicate(request);
            String content = result == null ? null : ChatCompletionUtil.getFirstAnswer(result);
            System.out.println(content == null ? "" : content);
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        agent.streamCommunicate(request).subscribe(chunk -> {
            String content = ChatCompletionUtil.getFirstAnswer(chunk);
            if (content != null) {
                System.out.print(content);
            }
        }, error -> {
            error.printStackTrace();
            latch.countDown();
        }, () -> {
            System.out.println();
            latch.countDown();
        });
        latch.await();
    }

    private Observable<LydaasChatResponse> streamResponses(ChatCompletionRequest request) {
        return Observable.create(emitter -> {
            validateRequest(request);
            String url = resolveApiUrl();
            LydaasChatRequest lydaasRequest = convertRequest(request);
            String requestJson = GSON.toJson(lydaasRequest);
            log.info("[pek_icdh][streamResponses] start, url={}, appId={}, tenantId={}, authorization={}, request={}",
                    url,
                    agentConfig.getAppId(),
                    agentConfig.getUserId(),
                    maskAuthorization(agentConfig.getToken()),
                    preview(requestJson));

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", agentConfig.getToken())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(requestJson, JSON))
                    .build();

            AtomicBoolean closed = new AtomicBoolean(false);
            final EventSource[] holder = new EventSource[1];
            emitter.setCancellable(() -> close(holder[0], client, closed));
            EventSource.Factory factory = EventSources.createFactory(client);
            holder[0] = factory.newEventSource(httpRequest, new EventSourceListener() {
                @Override
                public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                    log.info("[pek_icdh][streamResponses] onOpen, code={}, message={}, appId={}",
                            response.code(), response.message(), agentConfig.getAppId());
                    if (!response.isSuccessful()) {
                        String body = safeReadBody(response);
                        log.error("[pek_icdh][streamResponses] open failed, code={}, body={}",
                                response.code(), preview(body));
                        emitError(new RRException(LLMErrorConstants.OTHER_ERROR, body), eventSource);
                    }
                }

                @Override
                public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                    log.info("[pek_icdh][streamResponses] onEvent raw, id={}, type={}, length={}, data={}",
                            id, type, data.length(), preview(data));
                    if (emitter.isDisposed() || StrUtil.isBlank(data)) {
                        log.info("[pek_icdh][streamResponses] skip event because emitter disposed or data blank, disposed={}, id={}",
                                emitter.isDisposed(), id);
                        return;
                    }
                    LydaasChatResponse response = parseResponse(data);
                    if (response == null) {
                        log.info("[pek_icdh][streamResponses] skip event because parseResponse returned null, id={}", id);
                        return;
                    }
                    log.info("[pek_icdh][streamResponses] parsed event, msgId={}, msgType={}, chatStatus={}, isEnd={}, messageEnd={}, hasAnswer={}, docAskContent={}, contentText={}",
                            response.getMsgId(),
                            response.getMsgType(),
                            response.getChatStatus(),
                            response.getIsEnd(),
                            response.getMessageEnd(),
                            response.getHasAnswer(),
                            preview(response.getContent() == null ? null : response.getContent().getDocAskContent()),
                            preview(response.getContent() == null ? null : response.getContent().getText()));
                    if (!MSG_TYPE_AGENT_CHAT_MESSAGE.equals(response.getMsgType())) {
                        log.info("[pek_icdh][streamResponses] skip non-chat message, msgId={}, msgType={}",
                                response.getMsgId(), response.getMsgType());
                        return;
                    }
                    log.info("[pek_icdh][streamResponses] emit parsed response, msgId={}, terminal={}",
                            response.getMsgId(), isTerminal(response));
                    emitter.onNext(response);
                    if (isTerminal(response)) {
                        log.info("[pek_icdh][streamResponses] terminal event received, msgId={}, chatStatus={}, isEnd={}",
                                response.getMsgId(), response.getChatStatus(), response.getIsEnd());
                        complete(eventSource);
                    }
                }

                @Override
                public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                    if (closed.get()) {
                        log.info("[pek_icdh][streamResponses] onFailure ignored because already closed");
                        return;
                    }
                    String body = response == null ? null : safeReadBody(response);
                    String message = StrUtil.isNotBlank(body) ? body : (t == null ? "lydaas stream failed" : t.getMessage());
                    log.error("[pek_icdh][streamResponses] onFailure, appId={}, code={}, error={}, throwable={}",
                            agentConfig.getAppId(),
                            response == null ? null : response.code(),
                            preview(message),
                            t == null ? null : t.getClass().getName(), t);
                    emitError(new RRException(LLMErrorConstants.OTHER_ERROR, message), eventSource);
                }

                @Override
                public void onClosed(@NotNull EventSource eventSource) {
                    log.info("[pek_icdh][streamResponses] onClosed, appId={}", agentConfig.getAppId());
                    complete(eventSource);
                }

                private void emitError(RRException exception, EventSource eventSource) {
                    log.error("[pek_icdh][streamResponses] emitError, code={}, msg={}",
                            exception.getCode(), preview(exception.getMsg()));
                    if (!emitter.isDisposed()) {
                        emitter.onError(exception);
                    }
                    close(eventSource, client, closed);
                }

                private void complete(EventSource eventSource) {
                    if (!closed.compareAndSet(false, true)) {
                        log.info("[pek_icdh][streamResponses] complete ignored because already closed");
                        return;
                    }
                    log.info("[pek_icdh][streamResponses] complete stream, appId={}", agentConfig.getAppId());
                    if (!emitter.isDisposed()) {
                        emitter.onComplete();
                    }
                    eventSource.cancel();
                    client.dispatcher().executorService().shutdown();
                }
            });
        });
    }

    private LydaasChatRequest convertRequest(ChatCompletionRequest request) {
        List<LydaasChatDataItem> historyDataList = loadConversationDataList(request == null ? null : request.getSessionId());
        return LydaasChatRequest.builder()
                .query(extractQuery(request))
                .agentInstanceId(agentConfig.getAppId())
                .bizInvokeFrom(BIZ_INVOKE_FROM)
                .tenantId(agentConfig.getUserId())
                .isStream(request == null ? null : request.getStream())
                .dataList(historyDataList.isEmpty() ? null : historyDataList)
                .build();
    }

    private void validateRequest(ChatCompletionRequest request) {
        if (request == null) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "request is null");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "request messages is empty");
        }
        if (StrUtil.isBlank(agentConfig.getToken())) {
            throw new RRException(LLMErrorConstants.INVALID_AUTHENTICATION_ERROR, "lydaas authorization header is empty");
        }
        if (StrUtil.isBlank(agentConfig.getAppId())) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "lydaas agentInstanceId is empty");
        }
        if (StrUtil.isBlank(agentConfig.getUserId())) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "lydaas tenantId is empty");
        }
        if (StrUtil.isBlank(extractQuery(request))) {
            throw new RRException(LLMErrorConstants.INVALID_REQUEST_ERROR, "request query is empty");
        }
    }

    private String extractQuery(ChatCompletionRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        int lastIndex = request.getMessages().size() - 1;
        String content = request.getMessages().get(lastIndex).getContent();
        return content == null ? null : content.trim();
    }

    private String resolveApiUrl() {
        String endpoint = StrUtil.blankToDefault(agentConfig.getEndpoint(), DEFAULT_BASE_URL);
        if (endpoint.endsWith(CHAT_PATH)) {
            return endpoint;
        }
        return StrUtil.removeSuffix(endpoint, "/") + CHAT_PATH;
    }

    private LydaasChatResponse parseResponse(String data) {
        String trimmed = StrUtil.trim(data);
        if (StrUtil.isBlank(trimmed) || "[DONE]".equals(trimmed)) {
            log.info("[pek_icdh][parseResponse] skip special event, data={}", preview(trimmed));
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
            LydaasChatResponse parsed = new LydaasChatResponse();
            parsed.setMsgId(getAsString(root, "msgId"));
            parsed.setMsgType(getAsString(root, "msgType"));
            parsed.setTotalTokens(getAsLong(root, "totalTokens"));
            parsed.setSpendTime(getAsLong(root, "spendTime"));
            parsed.setChatId(getAsString(root, "chatId"));
            parsed.setChatStatus(getAsString(root, "chatStatus"));
            parsed.setConversationId(getAsString(root, "conversationId"));
            parsed.setIsEnd(getAsBoolean(root, "isEnd"));
            parsed.setFirstMessageFlag(getAsBoolean(root, "firstMessageFlag"));
            parsed.setMessageEnd(getAsBoolean(root, "messageEnd"));
            parsed.setHasAnswer(getAsBoolean(root, "hasAnswer"));
            parsed.setContentType(getAsString(root, "contentType"));
            parsed.setContent(parseContent(root.get("content")));
            parsed.setThoughtChainContentInfo(parseThoughtChainContentInfo(root.get("thoughtChainContentInfo")));
            log.info("[pek_icdh][parseResponse] success, msgId={}, msgType={}, chatStatus={}",
                    parsed == null ? null : parsed.getMsgId(),
                    parsed == null ? null : parsed.getMsgType(),
                    parsed == null ? null : parsed.getChatStatus());
            return parsed;
        } catch (RuntimeException e) {
            log.error("[pek_icdh][parseResponse] json parse failed, data={}", preview(trimmed), e);
            return null;
        }
    }

    private String extractDocAskContent(LydaasChatResponse response) {
        if (response == null || response.getContent() == null) {
            log.info("[pek_icdh][extractDocAskContent] response/content is null, msgId={}",
                    response == null ? null : response.getMsgId());
            return null;
        }
        String raw = response.getContent().getDocAskContent();
        String extracted = extractSentenceText(raw);
        log.info("[pek_icdh][extractDocAskContent] msgId={}, raw={}, extracted={}",
                response.getMsgId(), preview(raw), preview(extracted));
        return extracted;
    }

    private String extractContentText(LydaasChatResponse response) {
        if (response == null || response.getContent() == null) {
            log.info("[pek_icdh][extractContentText] response/content is null, msgId={}",
                    response == null ? null : response.getMsgId());
            return null;
        }
        String text = StrUtil.trimToNull(response.getContent().getText());
        log.info("[pek_icdh][extractContentText] msgId={}, text={}",
                response.getMsgId(), preview(text));
        return text;
    }

    private String extractEffectiveText(LydaasChatResponse response) {
        String docAskContent = extractDocAskContent(response);
        if (StrUtil.isNotBlank(docAskContent)) {
            return docAskContent;
        }
        return extractContentText(response);
    }

    private String extractSentenceText(String docAskContent) {
        if (StrUtil.isBlank(docAskContent)) {
            log.info("[pek_icdh][extractSentenceText] blank docAskContent");
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(docAskContent);
            if (root.isJsonArray()) {
                String sentence = extractSentenceFromArray(root.getAsJsonArray());
                log.info("[pek_icdh][extractSentenceText] parsed array, sentence={}", preview(sentence));
                return sentence;
            }
            if (root.isJsonObject()) {
                String sentence = extractSentenceFromObject(root.getAsJsonObject());
                log.info("[pek_icdh][extractSentenceText] parsed object, sentence={}", preview(sentence));
                return sentence;
            }
        } catch (JsonSyntaxException ignored) {
            log.info("[pek_icdh][extractSentenceText] not json, fallback raw string");
        }
        return docAskContent;
    }

    private String extractSentenceFromArray(JsonArray jsonArray) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : jsonArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            String sentence = extractSentenceFromObject(element.getAsJsonObject());
            if (StrUtil.isNotBlank(sentence)) {
                sb.append(sentence);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String extractSentenceFromObject(JsonObject jsonObject) {
        JsonElement sentence = jsonObject.get("sentence");
        if (sentence == null || sentence.isJsonNull()) {
            return null;
        }
        return sentence.getAsString();
    }

    private String computeDelta(String previousFullText, String currentFullText) {
        String previous = StrUtil.nullToEmpty(previousFullText);
        String current = StrUtil.nullToEmpty(currentFullText);
        if (current.equals(previous)) {
            log.info("[pek_icdh][computeDelta] unchanged text, previous={}, current={}",
                    preview(previous), preview(current));
            return "";
        }
        if (current.startsWith(previous)) {
            String delta = current.substring(previous.length());
            log.info("[pek_icdh][computeDelta] prefix delta, delta={}", preview(delta));
            return delta;
        }
        log.info("[pek_icdh][computeDelta] full replace delta, previous={}, current={}",
                preview(previous), preview(current));
        return current;
    }

    private List<LydaasChatDataItem> loadConversationDataList(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            log.info("[pek_icdh][cache] skip history lookup because sessionId is blank");
            return Collections.emptyList();
        }
        String conversationId = SESSION_CONVERSATION_CACHE.get(sessionId);
        if (StrUtil.isBlank(conversationId)) {
            log.info("[pek_icdh][cache] session miss, sessionId={}", sessionId);
            return Collections.emptyList();
        }
        List<LydaasChatDataItem> dataList = CONVERSATION_HISTORY_CACHE.get(conversationId);
        if (dataList == null || dataList.isEmpty()) {
            log.info("[pek_icdh][cache] conversation miss, sessionId={}, conversationId={}",
                    sessionId, conversationId);
            return Collections.emptyList();
        }
        log.info("[pek_icdh][cache] hit, sessionId={}, conversationId={}, historySize={}",
                sessionId, conversationId, dataList.size());
        return new ArrayList<>(dataList);
    }

    private void saveConversationContext(String sessionId, LydaasChatResponse response) {
        if (StrUtil.isBlank(sessionId)) {
            log.info("[pek_icdh][cache] skip save because sessionId is blank");
            return;
        }
        if (response == null) {
            log.info("[pek_icdh][cache] skip save because response is null, sessionId={}", sessionId);
            return;
        }
        String conversationId = response.getConversationId();
        String chatId = response.getChatId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(chatId)) {
            log.info("[pek_icdh][cache] skip save because conversationId/chatId missing, sessionId={}, conversationId={}, chatId={}",
                    sessionId, conversationId, chatId);
            return;
        }
        SESSION_CONVERSATION_CACHE.put(sessionId, conversationId);
        List<LydaasChatDataItem> existing = CONVERSATION_HISTORY_CACHE.get(conversationId);
        List<LydaasChatDataItem> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (containsChatId(updated, chatId)) {
            log.info("[pek_icdh][cache] skip duplicate chatId, sessionId={}, conversationId={}, chatId={}, historySize={}",
                    sessionId, conversationId, chatId, updated.size());
            CONVERSATION_HISTORY_CACHE.put(conversationId, updated);
            return;
        }
        updated.add(LydaasChatDataItem.builder()
                .conversationId(conversationId)
                .chatId(chatId)
                .build());
        CONVERSATION_HISTORY_CACHE.put(conversationId, updated);
        log.info("[pek_icdh][cache] saved, sessionId={}, conversationId={}, chatId={}, historySize={}",
                sessionId, conversationId, chatId, updated.size());
    }

    private boolean containsChatId(List<LydaasChatDataItem> dataList, String chatId) {
        if (dataList == null || dataList.isEmpty() || StrUtil.isBlank(chatId)) {
            return false;
        }
        for (LydaasChatDataItem item : dataList) {
            if (item != null && chatId.equals(item.getChatId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTerminal(LydaasChatResponse response) {
        return response != null && (Boolean.TRUE.equals(response.getIsEnd())
                || CHAT_STATUS_END.equals(response.getChatStatus())
                || CHAT_STATUS_ERROR.equals(response.getChatStatus()));
    }

    private boolean isBusinessError(LydaasChatResponse response) {
        return response != null && CHAT_STATUS_ERROR.equals(response.getChatStatus());
    }

    private String extractBusinessErrorTitle(LydaasChatResponse response) {
        if (response == null || response.getThoughtChainContentInfo() == null
                || response.getThoughtChainContentInfo().getThoughtChainList() == null
                || response.getThoughtChainContentInfo().getThoughtChainList().isEmpty()) {
            return "lydaas agent response error";
        }
        List<LydaasThoughtChainNode> thoughtChainList = response.getThoughtChainContentInfo().getThoughtChainList();
        for (int i = thoughtChainList.size() - 1; i >= 0; i--) {
            LydaasThoughtChainNode node = thoughtChainList.get(i);
            if (node != null && THOUGHT_STATUS_FAIL.equalsIgnoreCase(node.getStatus()) && StrUtil.isNotBlank(node.getTitle())) {
                return node.getTitle();
            }
        }
        for (int i = thoughtChainList.size() - 1; i >= 0; i--) {
            LydaasThoughtChainNode node = thoughtChainList.get(i);
            if (node != null && StrUtil.isNotBlank(node.getTitle())) {
                return node.getTitle();
            }
        }
        return "lydaas agent response error";
    }

    private LydaasChatContent parseContent(JsonElement contentElement) {
        if (contentElement == null || contentElement.isJsonNull() || contentElement.isJsonPrimitive()) {
            return null;
        }
        JsonObject contentObject = contentElement.getAsJsonObject();
        LydaasChatContent content = new LydaasChatContent();
        content.setDocAskContent(getAsString(contentObject, "docAskContent"));
        content.setText(getAsString(contentObject, "text"));
        content.setContentType(getAsString(contentObject, "contentType"));
        content.setAudioFile(getAsString(contentObject, "audioFile"));
        return content;
    }

    private LydaasThoughtChainContentInfo parseThoughtChainContentInfo(JsonElement thoughtChainElement) {
        if (thoughtChainElement == null || thoughtChainElement.isJsonNull() || !thoughtChainElement.isJsonObject()) {
            return null;
        }
        JsonArray thoughtChainArray = thoughtChainElement.getAsJsonObject().getAsJsonArray("thoughtChainList");
        if (thoughtChainArray == null) {
            return null;
        }
        List<LydaasThoughtChainNode> thoughtChainList = new ArrayList<>();
        for (JsonElement element : thoughtChainArray) {
            if (element == null || element.isJsonNull() || !element.isJsonObject()) {
                continue;
            }
            JsonObject nodeObject = element.getAsJsonObject();
            thoughtChainList.add(new LydaasThoughtChainNode(
                    getAsString(nodeObject, "status"),
                    getAsString(nodeObject, "title")));
        }
        return new LydaasThoughtChainContentInfo(thoughtChainList);
    }

    private String getAsString(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return null;
        }
        JsonElement value = jsonObject.get(fieldName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return GSON.toJson(value);
    }

    private Long getAsLong(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return null;
        }
        JsonElement value = jsonObject.get(fieldName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.getAsLong();
    }

    private Boolean getAsBoolean(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return null;
        }
        JsonElement value = jsonObject.get(fieldName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.getAsBoolean();
    }

    private ChatCompletionResult toChatCompletionResult(String message, String model, String finishReason) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(UUID.randomUUID().toString());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        result.setModel(model);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(StrUtil.nullToEmpty(message));
        chatMessage.setRole("assistant");
        choice.setMessage(chatMessage);
        choice.setFinish_reason(finishReason);
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private String safeReadBody(Response response) {
        if (response == null || response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private void close(EventSource eventSource, OkHttpClient client, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            log.info("[pek_icdh][close] skip because already closed");
            return;
        }
        log.info("[pek_icdh][close] closing eventSource and client, appId={}", agentConfig.getAppId());
        if (eventSource != null) {
            eventSource.cancel();
        }
        client.dispatcher().executorService().shutdown();
    }

    private String preview(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\n", "\\n").replace("\r", "\\r");
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...(" + normalized.length() + " chars)";
    }

    private String maskAuthorization(String authorization) {
        if (StrUtil.isBlank(authorization)) {
            return authorization;
        }
        if (authorization.length() <= 18) {
            return authorization;
        }
        return authorization.substring(0, 12) + "***" + authorization.substring(authorization.length() - 6);
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("missing env: " + key);
        }
        return value;
    }
}
