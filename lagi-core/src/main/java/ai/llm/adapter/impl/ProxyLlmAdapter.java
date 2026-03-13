package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.hook.HookService;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.utils.PriorityLock;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import cn.hutool.core.bean.BeanUtil;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@LLM(modelNames = {"qwen-turbo","qwen-plus","qwen-max","qwen-max-1201","qwen-max-longcontext", "DeepSeek-R1-Distill-Qwen-32B"})
public class ProxyLlmAdapter extends ModelService implements ILlmAdapter {

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



    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        // Prevent stack overflow caused by calling large models within the hook function
        if(!Boolean.TRUE.equals(request.getEnableHook())) {
            request.setEnableHook(true);
            request = hookService.beforeModel(request);
        }
        if(!(request instanceof EnhanceChatCompletionRequest)) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return hookService.AfterModel(completions);
        }
        if(this.priorityLock == null) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return hookService.AfterModel(completions);
        }
        Integer priority = ((EnhanceChatCompletionRequest) request).getPriority();
        if(priority == null) {
            ChatCompletionResult completions = llmAdapter.completions(request);
            return hookService.AfterModel(completions);
        }
        try {
//            log.info("locking priority {}", priority);
            this.priorityLock.lock(priority);
//            log.info("get lock priority {}", priority);
            ((EnhanceChatCompletionRequest) request).setPriority(null);
            ChatCompletionResult completions = llmAdapter.completions(request);
            completions = hookService.AfterModel(completions);
            return completions;
        } finally {
//            log.info("Unlocking priority {}", priority);
            this.priorityLock.unlock(priority);
        }
    }




    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        // Prevent stack overflow caused by calling large models within the hook function
        if(!Boolean.TRUE.equals(chatCompletionRequest.getEnableHook())) {
            chatCompletionRequest.setEnableHook(true);
            chatCompletionRequest = hookService.beforeModel(chatCompletionRequest);
        }
        if(!(chatCompletionRequest instanceof EnhanceChatCompletionRequest)) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            return hookService.streamApply(chatCompletionResultObservable);
        }
        if(this.priorityLock == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            return hookService.streamApply(chatCompletionResultObservable);
        }
        Integer priority = ((EnhanceChatCompletionRequest) chatCompletionRequest).getPriority();
        if(priority == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = llmAdapter.streamCompletions(chatCompletionRequest);
            return hookService.streamApply(chatCompletionResultObservable);
        }
        Observable<ChatCompletionResult> completions = null;
        try {
//            log.info("stream locking priority {}", priority);
            this.priorityLock.lock(priority);
//            log.info("stream get lock priority {}", priority);
            ((EnhanceChatCompletionRequest) chatCompletionRequest).setPriority(null);
            completions = llmAdapter.streamCompletions(chatCompletionRequest);
            completions = hookService.streamApply(completions);
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
