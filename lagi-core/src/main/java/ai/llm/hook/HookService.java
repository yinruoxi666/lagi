package ai.llm.hook;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.BeanManageUtil;
import io.reactivex.Observable;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HookService {

    private static final Logger log = LoggerFactory.getLogger(HookService.class);

    @Getter
    private static final HookService instance = new HookService();

    private HookService() {
    }

    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        if (request == null || BeanManageUtil.getBeansByType(BeforeModel.class) == null) {
            return request;
        }
        for (BeforeModel beforeModel : BeanManageUtil.getBeansByType(BeforeModel.class)) {
            try {
                ChatCompletionRequest next = beforeModel.beforeModel(request);
                if (next != null) {
                    request = next;
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return request;
    }

    public ChatCompletionResult AfterModel(ChatCompletionResult result) {
        if (result == null || BeanManageUtil.getBeansByType(AfterModel.class) == null) {
            return result;
        }
        for (AfterModel afterModel : BeanManageUtil.getBeansByType(AfterModel.class)) {
            try {
                ChatCompletionResult next = afterModel.apply(result);
                if (next != null) {
                    result = next;
                }
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

    public Observable<ChatCompletionResult> streamApply(Observable<ChatCompletionResult> result) {
        if (result == null || BeanManageUtil.getBeansByType(AfterModel.class) == null) {
            return result;
        }
        for (AfterModel afterModel : BeanManageUtil.getBeansByType(AfterModel.class)) {
            try {
                Observable<ChatCompletionResult> next = afterModel.stream(result);
                if (next != null) {
                    result = next;
                }
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

}
