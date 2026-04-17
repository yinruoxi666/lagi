package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.hook.HookService;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.ModelContext;
import ai.llm.utils.PriorityLock;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import cn.hutool.core.bean.BeanUtil;
import io.reactivex.Observable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public class ProxyLlmAdapter extends ModelService implements ILlmAdapter {

    @Getter
    private final ILlmAdapter llmAdapter;


    private PriorityLock priorityLock = null;
    private final HookService hookService = HookService.getInstance();

    public ProxyLlmAdapter(ILlmAdapter llmAdapter) {
        this.llmAdapter = llmAdapter;
        if(llmAdapter instanceof ModelService) {
            ModelService modelService = (ModelService) llmAdapter;
            BeanUtil.copyProperties(modelService, this);
            if(modelService.getConcurrency() != null) {
                this.priorityLock = new PriorityLock(modelService.getConcurrency());
            }
        }
    }


    private Observable<ChatCompletionResult> applyStreamHookOnce(ChatCompletionRequest request, ModelContext context) {
        if(Boolean.TRUE.equals(request.getEnableAfter())) {
            request.setEnableAfter(null);
            return hookService != null ? hookService.streamApply(context) : context.getStreamResult();
        }
        return context.getStreamResult();
    }

    private ChatCompletionResult applyAfterHookOnce(ChatCompletionRequest request, ModelContext context, ChatCompletionResult completions) {
        if(Boolean.TRUE.equals(request.getEnableAfter())) {
            request.setEnableAfter(null);
            context.setResult(completions);
            return hookService != null ? hookService.AfterModel(context) : completions;
        }
        return completions;
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        // Prevent stack overflow caused by calling large models within the hook function
        ModelContext context = ModelContext.builder().request(request).adapter(this.llmAdapter).build();
        if(Boolean.TRUE.equals(request.getEnableHook())) {
            request.setEnableHook(null);
            if (hookService != null) {
                ChatCompletionRequest afterHook = hookService.beforeModel(context);
                if (afterHook != null) {
                    request = afterHook;
                }
            }
        }
        if(!(request instanceof EnhanceChatCompletionRequest)) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        if(this.priorityLock == null) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        Integer priority = ((EnhanceChatCompletionRequest) request).getPriority();
        if(priority == null) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        try {
//            log.info("locking priority {}", priority);
            this.priorityLock.lock(priority);
//            log.info("get lock priority {}", priority);
            ((EnhanceChatCompletionRequest) request).setPriority(null);
            ChatCompletionResult completions = llmAdapter.completions(request);
            return applyAfterHookOnce(request, context, completions);
        } finally {
//            log.info("Unlocking priority {}", priority);
            this.priorityLock.unlock(priority);
        }
    }




    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        // Prevent stack overflow caused by calling large models within the hook function
        ModelContext context = ModelContext.builder().request(chatCompletionRequest).adapter(this.llmAdapter).build();
        if(Boolean.TRUE.equals(chatCompletionRequest.getEnableHook())) {
            chatCompletionRequest.setEnableHook(null);
            if (hookService != null) {
                ChatCompletionRequest afterHook = hookService.beforeModel(context);
                if (afterHook != null) {
                    chatCompletionRequest = afterHook;
                }
            }
        }
        if(!(chatCompletionRequest instanceof EnhanceChatCompletionRequest)) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        if(this.priorityLock == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        Integer priority = ((EnhanceChatCompletionRequest) chatCompletionRequest).getPriority();
        if(priority == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        Observable<ChatCompletionResult> completions = null;
        try {
//            log.info("stream locking priority {}", priority);
            this.priorityLock.lock(priority);
//            log.info("stream get lock priority {}", priority);
            ((EnhanceChatCompletionRequest) chatCompletionRequest).setPriority(null);
            completions = llmAdapter.streamCompletions(chatCompletionRequest);
            context.setStreamResult(completions);
            completions = applyStreamHookOnce(chatCompletionRequest, context);
            if(completions != null) {
                return completions.doFinally(() -> {
//                    log.info("stream Unlocking priority {}", priority);
                    this.priorityLock.unlock(priority);
                });
            } else {
                return null;
            }
        } finally {
            if(completions == null) {
                this.priorityLock.unlock(priority);
            }
        }
    }
}
