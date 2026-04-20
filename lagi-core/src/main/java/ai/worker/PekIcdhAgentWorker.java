package ai.worker;

import ai.agent.chat.lydaas.PekIcdhAgent;
import ai.common.exception.RRException;
import ai.manager.AgentManager;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class PekIcdhAgentWorker {
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        PekIcdhAgent agent = getAgent();
        return agent.communicate(request);
    }
    public Observable<ChatCompletionResult>  work(String workerName, ChatCompletionRequest request) throws IOException {
        try {
            PekIcdhAgent agent = getAgent();
            return agent.streamCommunicate(request);
        } catch (Exception e) {
            log.error("worker {} work error", workerName, e);
            return Observable.error(e);
        }
    }

    private PekIcdhAgent getAgent() {
        Object agent = AgentManager.getInstance().get("pek_icdh");
        if (agent == null) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "pek_icdh agent is not registered");
        }
        if (!(agent instanceof PekIcdhAgent)) {
            throw new RRException(LLMErrorConstants.OTHER_ERROR, "pek_icdh agent type is invalid");
        }
        return (PekIcdhAgent) agent;
    }
}
