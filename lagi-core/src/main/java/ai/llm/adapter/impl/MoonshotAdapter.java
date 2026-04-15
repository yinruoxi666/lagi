package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.MoonshotConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;

import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


@LLM(modelNames = {"moonshot-v1-8k","moonshot-v1-32k","moonshot-v1-128k"})
public class MoonshotAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MoonshotAdapter.class);
    private static final int HTTP_TIMEOUT = 15 * 1000;
    private static final String COMPLETIONS_URL = "https://api.moonshot.cn/v1/chat/completions";

    private Proxy getNetworkProxy() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", 7890);
        System.out.println(isProxyAvailable(inetSocketAddress));
        if (!isProxyAvailable(inetSocketAddress)) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, inetSocketAddress);
    }

    private boolean isProxyAvailable(InetSocketAddress proxyAddress) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(proxyAddress, 2000);
            return true;
        } catch (Exception e) {
            logger.debug("检测代理 {}:{} 不可用，异常信息：{}",
                    proxyAddress.getHostName(), proxyAddress.getPort(), e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    logger.warn("关闭检测代理的Socket时出错", e);
                }
            }
        }
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        LlmApiResponse completions = OpenAiApiUtil.completions(apiKey, COMPLETIONS_URL, HTTP_TIMEOUT, new Gson().toJson(chatCompletionRequest),
                MoonshotConvert::convertChatCompletionResult, MoonshotConvert::convertByResponse, headers, getNetworkProxy());
        if(completions.getCode() != 200) {
            logger.error("moonshot  api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }



    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, COMPLETIONS_URL, HTTP_TIMEOUT, new Gson().toJson(chatCompletionRequest),
                MoonshotConvert::convertStreamLine2ChatCompletionResult, MoonshotConvert::convertByResponse, headers, getNetworkProxy());
        if(completions.getCode() != 200) {
            logger.error("moonshot api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }
}
