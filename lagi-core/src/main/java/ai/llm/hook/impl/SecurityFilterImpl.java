package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.annotation.Value;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.SensitiveWordUtil;
import io.reactivex.Observable;

import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(1)
@Component
public class SecurityFilterImpl implements BeforeModel, AfterModel {

    @Value("${filters[0].filter_window_length:1}")
    private Integer queueCapacity;

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        if (request.getMessages() == null || request.getMessages().isEmpty()) return request;
        ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
        String raw = last.getContent();
        String filter = SensitiveWordUtil.filter(raw, SensitiveWordUtil.INPUT_RULE_TYPE);
        last.setContent(filter);
        return request;
    }

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        return SensitiveWordUtil.filter4ChatCompletionResult(context.getResult());
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        Observable<ChatCompletionResult> source = context.getStreamResult();
        Observable<ChatCompletionResult> processedSource = source.filter(result -> true);
        return Observable.create(emitter -> {
            Queue<ChatCompletionResult> cacheQueue = new ArrayBlockingQueue<>(queueCapacity);
            List<String> contents = new Vector<>(queueCapacity);
            AtomicBoolean streamSensitiveRecorded = new AtomicBoolean(false);
            processedSource.subscribe(
                    chunk -> {
                        try {
                            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                                return;
                            }
                            String content = chunk.getChoices().get(0).getMessage().getContent();
                            contents.add(content);
                            String totalContent = String.join("", contents);
                            String nullOrReplaceContent = SensitiveWordUtil.getNullOrReplaceContent(totalContent);
                            if(nullOrReplaceContent != null) {
                                if (streamSensitiveRecorded.compareAndSet(false, true)) {
                                    SensitiveWordUtil.recordOutputStreamFilter(totalContent);
                                }
                                int size = cacheQueue.size();
                                for (int i = 0; i < size; i++) {
                                    ChatCompletionResult temp = cacheQueue.poll();
                                    if (temp != null && i != 0) {
                                        temp.getChoices().get(0).getMessage().setContent(nullOrReplaceContent);
                                    }
                                    cacheQueue.offer(temp);
                                }
                                for (int i = contents.size() - size; i < contents.size(); i++) {
                                    contents.set(i, nullOrReplaceContent);
                                }
                                chunk.getChoices().get(0).getMessage().setContent(nullOrReplaceContent);
                            }
                            if(cacheQueue.size() < queueCapacity) {
                                cacheQueue.offer(chunk);
                            } else {
                                ChatCompletionResult toEmit = cacheQueue.poll();
                                cacheQueue.offer(chunk);
                                if(toEmit.getChoices().get(0).getDelta() == null) {
                                    toEmit.getChoices().get(0).setDelta(toEmit.getChoices().get(0).getMessage());
                                }
                                emitter.onNext(toEmit);
//                                System.out.println("send: " + toEmit.getChoices().get(0).getMessage().getContent());
                            }
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    },
                    emitter::onError,
                    () -> {
                        while (!cacheQueue.isEmpty()) {
                            ChatCompletionResult remaining = cacheQueue.poll();
                            if (remaining != null) {
                                if (remaining.getChoices() == null || remaining.getChoices().isEmpty()) {
                                    continue;
                                }
                                if(remaining.getChoices().get(0).getDelta() == null) {
                                    remaining.getChoices().get(0).setDelta(remaining.getChoices().get(0).getMessage());
                                }
                                emitter.onNext(remaining);
//                                System.out.println("send: " + remaining.getChoices().get(0).getMessage().getContent());
                            }
                        }
                        emitter.onComplete();
                    }
            );

            emitter.setCancellable(() -> {
                cacheQueue.clear();
                contents.clear();
            });
        });
    }
}