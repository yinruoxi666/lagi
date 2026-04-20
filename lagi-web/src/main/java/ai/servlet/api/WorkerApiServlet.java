package ai.servlet.api;

import ai.audio.pojo.AsrResponse;
import ai.common.exception.RRException;
import ai.llm.pojo.ArvryuyiChatCompletionRequest;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.common.pojo.IndexSearchData;
import ai.llm.pojo.GetRagContext;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.BaseServlet;
import ai.utils.SensitiveWordUtil;
import ai.worker.DefaultWorker;
import ai.utils.SensitiveWordUtil;
import ai.worker.ArvryuyiWorker;
import ai.worker.PekIcdhAgentWorker;
import ai.worker.audio.Asr4FlightsWorker;
import ai.worker.llmIntent.LlmIntentWorker;
import ai.worker.pojo.Asr4FlightData;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Slf4j
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 5)
public class WorkerApiServlet extends BaseServlet {

    private final Logger logger = LoggerFactory.getLogger(WorkerApiServlet.class);
    private final Asr4FlightsWorker asr4FlightsWorker = new Asr4FlightsWorker();
    private final DefaultWorker defaultWorker = new DefaultWorker();
    private final LlmIntentWorker llmIntentWorker = new LlmIntentWorker();


    private static final Gson gson = new Gson();

    private final ArvryuyiWorker arvryuyiWorker = new ArvryuyiWorker();
    private final PekIcdhAgentWorker pekIcdhAgentWorker = new PekIcdhAgentWorker();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("uploadVoice") || method.equals("asr4flights")) {
            this.asr4flights(req, resp);
        } else if (method.equals("completions")) {
            this.completions(req, resp);
        } else if (method.equals("arvryuyiCompletions")) {
            this.arvryuyiCompletions(req, resp);
        } else if (method.equals("pekIcdhCompletions")) {
            this.pekIcdhCompletion(req, resp);
        }
    }

    public void completions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        ChatCompletionRequest chatCompletionRequest = reqBodyToObj(req, ChatCompletionRequest.class);
        ChatCompletionResult chatCompletionResult = llmIntentWorker.process(chatCompletionRequest, null);
        chatCompletionResult = SensitiveWordUtil.filter(chatCompletionResult);
        responsePrint(resp, gson.toJson(chatCompletionResult));
    }

    public void arvryuyiCompletions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();
        resp.setContentType("application/json;charset=utf-8");
        ArvryuyiChatCompletionRequest chatCompletionRequest = reqBodyToObj(req, ArvryuyiChatCompletionRequest.class);
        if (chatCompletionRequest.getStream()) {
            Observable<ChatCompletionResult> result = arvryuyiWorker.work("arvryuyi", chatCompletionRequest);
            resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
            streamOutPrint(result, null, null, out);
        } else {
            ChatCompletionResult chatCompletionResult = arvryuyiWorker.completions(chatCompletionRequest);
            responsePrint(resp, gson.toJson(chatCompletionResult));
        }
    }
    public void pekIcdhCompletion(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ChatCompletionRequest chatCompletionRequest = reqBodyToObj(req, ChatCompletionRequest.class);
        if (chatCompletionRequest.getStream()) {
            resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("Connection", "keep-alive");
            PrintWriter out = resp.getWriter();
            Observable<ChatCompletionResult> result = pekIcdhAgentWorker.work("pek-icdh", chatCompletionRequest);
            streamOutPrint(result, null, null, out);
        } else {
            resp.setContentType("application/json;charset=utf-8");
            ChatCompletionResult chatCompletionResult = pekIcdhAgentWorker.completions(chatCompletionRequest);
            responsePrint(resp, gson.toJson(chatCompletionResult));
        }
    }
    public void asr4flights(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Part filePart = request.getPart("audioFile");
        String fileName = getFileName(filePart);
        String os = System.getProperty("os.name").toLowerCase();

        String tempFolder;
        if (os.contains("win")) {
            tempFolder = "C:/temp/";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            tempFolder = "/tmp/";
        } else {
            tempFolder = "/var/tmp/";
        }

        File tempDir = new File(tempFolder);
        if (!tempDir.exists()) {
            tempDir.mkdirs(); // 创建临时文件夹及其父文件夹（如果不存在）
        }

        String savePath = tempFolder;
        String resPath = savePath + fileName;
        AsrResponse result;
        try (InputStream input = filePart.getInputStream();
             OutputStream output = Files.newOutputStream(Paths.get(savePath + fileName))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            Asr4FlightData build = Asr4FlightData.builder().resPath(resPath).build();
            result = asr4FlightsWorker.call(build);
        } catch (IOException e) {
            result = new AsrResponse(1, "识别失败");
            e.printStackTrace();
        }
        response.setHeader("Content-Type", "application/json;charset=utf-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
        out.close();
    }

    private String getFileName(Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(content.indexOf('=') + 1).trim()
                        .replace("\"", "");
            }
        }
        return null;
    }

    private void streamOutPrint(Observable<ChatCompletionResult> observable, GetRagContext context, List<IndexSearchData> indexSearchDataList, PrintWriter out) {
        if (observable == null) {
            outputErrorChunk(out, new RRException(500, "stream observable is null"));
            outputDoneChunk(out);
            out.flush();
            out.close();
            return;
        }
        final ChatCompletionResult[] lastResult = {null, null};
        final Object completeSignal = new Object();
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        observable.subscribe(
                queue::offer,
                e -> {
                    logger.error("", e);
                    queue.offer(e == null ? new RRException(500, "unknown stream error") : e);
                    queue.offer(completeSignal);
                },
                () -> queue.offer(completeSignal)
        );
        boolean hasError = false;
        try {
            while (true) {
                Object item = queue.take();
                if (item == completeSignal) {
                    break;
                }
                if (item instanceof Throwable) {
                    outputErrorChunk(out, (Throwable) item);
                    hasError = true;
                    continue;
                }
                ChatCompletionResult data = (ChatCompletionResult) item;
                lastResult[0] = data;
                ChatCompletionResult filter = SensitiveWordUtil.filter(data);
                String msg = gson.toJson(filter);
                out.print("data: " + msg + "\n\n");
                out.flush();
                if (lastResult[1] == null) {
                    lastResult[1] = data;
                } else {
                    for (int i = 0; i < lastResult[1].getChoices().size(); i++) {
                        ChatCompletionChoice choice = lastResult[1].getChoices().get(i);
                        ChatCompletionChoice chunkChoice = data.getChoices().get(i);
                        String chunkContent = chunkChoice.getMessage().getContent();
                        String content = choice.getMessage().getContent();
                        choice.getMessage().setContent(content + chunkContent);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("stream interrupted", e);
            outputErrorChunk(out, e);
            hasError = true;
        }
        if (!hasError && lastResult[0] != null) {
            extracted(lastResult,indexSearchDataList,context, out);
            lastResult[0].setChoices(lastResult[1].getChoices());
        } else {
            outputDoneChunk(out);
        }
        out.flush();
        out.close();
    }

    private void extracted(ChatCompletionResult[] lastResult, List<IndexSearchData> indexSearchDataList, GetRagContext ragContext, PrintWriter out) {
        if (lastResult[0] != null && !lastResult[0].getChoices().isEmpty()
                && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {

            out.print("data: " + gson.toJson(lastResult[0]) + "\n\n");
        }
        outputDoneChunk(out);
    }

    private void outputDoneChunk(PrintWriter out) {
        out.print("data: " + "[DONE]" + "\n\n");
    }

    private void outputErrorChunk(PrintWriter out, Throwable throwable) {
        Map<String, Object> error = new HashMap<>();
        if (throwable instanceof RRException) {
            RRException rrException = (RRException) throwable;
            error.put("code", rrException.getCode());
            error.put("error", rrException.getMsg());
        } else {
            error.put("code", 500);
            error.put("error", throwable == null ? "unknown error" : throwable.getMessage());
        }
        out.print("data: " + gson.toJson(error) + "\n\n");
    }
}
