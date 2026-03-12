package ai.llm.hook;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.BeanManageUtil;
import io.reactivex.Observable;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HookService {

    private static final Logger log = LoggerFactory.getLogger(HookService.class);

    private final List<BeforeModel> beforeModels;
    private final List<AfterModel> afterModels;

    @Getter
    private static final HookService instance = new HookService();

    private HookService() {
        beforeModels = BeanManageUtil.getBeansByType(BeforeModel.class);
        afterModels = BeanManageUtil.getBeansByType(AfterModel.class);
    }

    public ChatCompletionRequest beforeModel(ChatCompletionRequest request) {
        for (BeforeModel beforeModel : beforeModels) {
            try {
                request =  beforeModel.beforeModel(request);
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return request;
    }

    public ChatCompletionResult AfterModel(ChatCompletionResult result) {
        for (AfterModel afterModel : afterModels) {
            try {
                result = afterModel.apply(result);
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

    public Observable<ChatCompletionResult> streamApply(Observable<ChatCompletionResult> result) {
        for (AfterModel afterModel : afterModels) {
            try {
                result = afterModel.stream(result);
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

}
