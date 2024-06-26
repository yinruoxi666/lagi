package ai.servlet.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.llm.service.CompletionsService;
import ai.common.pojo.Configuration;
import ai.common.pojo.IndexSearchData;
import ai.migrate.service.VectorDbService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.BaseServlet;
import ai.utils.MigrateGlobal;
import ai.utils.qa.ChatCompletionUtil;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private static Configuration config = MigrateGlobal.config;
    private CompletionsService completionsService = new CompletionsService();
    private VectorDbService vectorDbService = new VectorDbService(config);

    private Logger logger = LoggerFactory.getLogger(LlmApiServlet.class);
    private ChatCompletionResult data;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("completions")) {
            this.completions(req, resp);
        } else if (method.equals("embeddings")) {
            this.embeddings(req, resp);
        }
    }

    private void completions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        ChatCompletionRequest chatCompletionRequest = reqBodyToObj(req, ChatCompletionRequest.class);
        List<IndexSearchData> indexSearchDataList;
        if (chatCompletionRequest.getCategory() != null && vectorDbService.vectorStoreEnabled()) {
            indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
            if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                addVectorDBContext(chatCompletionRequest, indexSearchDataList);
            }
        } else {
            indexSearchDataList = null;
        }
        if (chatCompletionRequest.getStream() != null && chatCompletionRequest.getStream()) {
            resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
            Observable<ChatCompletionResult> observable = completionsService.streamCompletions(chatCompletionRequest);
            PrintWriter out = resp.getWriter();
            final ChatCompletionResult[] lastResult = {null};
            observable.subscribe(
                    data -> {
                        lastResult[0] = data;
                        String msg = gson.toJson(data);
                        out.print("data: " + msg + "\n\n");
                        out.flush();
                    },
                    e -> logger.error("", e),
                    () -> extracted(lastResult, indexSearchDataList, req, out)
            );
            out.flush();
            out.close();
        } else {
            ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
            if (result != null && !result.getChoices().isEmpty()
                    && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                IndexSearchData indexData = indexSearchDataList.get(0);
                List<String> imageList = getImageFiles(indexData, req);
                for (int i = 0; i < result.getChoices().size(); i++) {
                    ChatMessage message = result.getChoices().get(i).getMessage();
                    message.setContext(indexData.getText());
                    message.setFilename(indexData.getFilename());
                    message.setFilepath(indexData.getFilepath());
                    message.setImageList(imageList);
                }
            }
            responsePrint(resp, toJson(result));
        }
    }
    private void extracted(ChatCompletionResult[] lastResult, List<IndexSearchData> indexSearchDataList,
                           HttpServletRequest req, PrintWriter out) {
        if (lastResult[0] != null && !lastResult[0].getChoices().isEmpty()
                && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
            IndexSearchData indexData = indexSearchDataList.get(0);
            List<String> imageList = getImageFiles(indexData, req);
            for (int i = 0; i < lastResult[0].getChoices().size(); i++) {
                ChatMessage message = lastResult[0].getChoices().get(i).getMessage();
                message.setContent("");
                message.setContext(indexData.getText());
                message.setFilename(indexData.getFilename());
                message.setFilepath(indexData.getFilepath());
                message.setImageList(imageList);
            }
            out.print("data: " + gson.toJson(lastResult[0]) + "\n\n");
        }
        out.print("data: " + "[DONE]" + "\n\n");
    }

    private List<String> getImageFiles(IndexSearchData indexData, HttpServletRequest req) {
        List<String> imageList = null;

        if (indexData.getImage() != null && !indexData.getImage().isEmpty()) {
            imageList = new ArrayList<>();
            List<JsonObject> imageObjectList = gson.fromJson(indexData.getImage(), new TypeToken<List<JsonObject>>() {
            }.getType());
            for (JsonObject image : imageObjectList) {
                String url = image.get("path").getAsString();
                imageList.add(url);
            }
        }
        return imageList;
    }

    private void addVectorDBContext(ChatCompletionRequest request, List<IndexSearchData> indexSearchDataList) {
        String lastMessage = ChatCompletionUtil.getLastMessage(request);
        String contextText = indexSearchDataList.get(0).getText();
        String prompt = ChatCompletionUtil.getPrompt(contextText, lastMessage);
        ChatCompletionUtil.setLastMessage(request, prompt);
    }

    private void embeddings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OpenAIEmbeddingRequest request = gson.fromJson(requestToJson(req), OpenAIEmbeddingRequest.class);
        Embeddings embeddings = EmbeddingFactory.getEmbedding(config.getLLM().getEmbedding());
        List<List<Float>> embeddingDataList = embeddings.createEmbedding(request.getInput());
        Map<String, Object> result = new HashMap<>();
        if (embeddingDataList.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", embeddingDataList);
        }
        responsePrint(resp, toJson(result));
    }
}
