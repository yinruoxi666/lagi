package ai.worker;

import ai.llm.adapter.impl.ArvryuyiAdapter;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class ArvryuyiWorker {
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        try {
            ArvryuyiAdapter arvryuyiAdapter = new ArvryuyiAdapter();
            return arvryuyiAdapter.completions(request);
        } catch (Exception e) {
            log.error("completions error", e);
        }
        return null;
    }
    public Observable<ChatCompletionResult>  work(String workerName, ChatCompletionRequest request) throws IOException {
        try {

            ArvryuyiAdapter arvryuyiAdapter = new ArvryuyiAdapter();

            return arvryuyiAdapter.streamCompletions(request);
        } catch (Exception e) {
            log.error("worker {} work error", workerName, e);
        }
        return null;
    }
}
