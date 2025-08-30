package ai.servlet.api;

import ai.bigdata.BigdataService;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.UserRagSetting;
import ai.migrate.service.UploadFileService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.servlet.BaseServlet;
import ai.servlet.dto.VectorDeleteRequest;
import ai.servlet.dto.VectorSearchRequest;
import ai.servlet.dto.VectorUpsertRequest;
import ai.vector.VectorCacheLoader;
import ai.vector.VectorDbService;
import ai.vector.VectorStoreService;
import ai.vector.pojo.*;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class VectorApiServlet extends BaseServlet {
    private final VectorStoreService vectorStoreService = new VectorStoreService();
    private final VectorDbService vectorDbService = new VectorDbService(null);
    private final BigdataService bigdataService = new BigdataService();
    private final UploadFileService uploadFileService = new UploadFileService();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.setSerializationInclusion(NON_NULL);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("query") || method.equals("chunkQuery")) {
            this.query(req, resp);
        } else if (method.equals("get") || method.equals("chunkGet")) {
            this.get(req, resp);
        } else if (method.equals("add")) {
            this.add(req, resp);
        } else if (method.equals("update")) {
            this.update(req, resp);
        } else if (method.equals("delete")) {
            this.delete(req, resp);
        } else if (method.equals("chunkAdd")) {
            this.chunkAdd(req, resp);
        } else if (method.equals("chunkUpdate")) {
            this.chunkUpdate(req, resp);
        } else if (method.equals("chunkDelete")) {
            this.chunkDelete(req, resp);
        } else if (method.equals("upsert")) {
            this.upsert(req, resp);
        } else if (method.equals("search")) {
            this.search(req, resp);
        } else if (method.equals("searchByMetadata")) {
            this.searchByMetadata(req, resp);
        } else if (method.equals("deleteById")) {
            this.deleteById(req, resp);
        } else if (method.equals("deleteByMetadata")) {
            this.deleteByMetadata(req, resp);
        } else if (method.equals("deleteCollection")) {
            this.deleteCollection(req, resp);
        } else if (method.equals("updateTextBlockSize")) {
            this.updateTextBlockSize(req, resp);
        } else if (method.equals("resetBlockSize")) {
            this.resetBlockSize(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("listCollections")) {
            this.listCollections(req, resp);
        } else if (method.equals("getTextBlockSize")) {
            this.getTextBlockSize(req, resp);
        }
    }

    private void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        GetEmbedding getEmbedding = objectMapper.readValue(json, GetEmbedding.class);
        List<IndexRecord> indexRecords = vectorStoreService.get(getEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", indexRecords);
        responsePrint(resp, toJson(result));
    }

    private void add(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        AddEmbedding addEmbedding = objectMapper.readValue(json, AddEmbedding.class);
        vectorStoreService.add(addEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void update(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        UpdateEmbedding updateEmbedding = objectMapper.readValue(json, UpdateEmbedding.class);
        vectorStoreService.update(updateEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        DeleteEmbedding deleteEmbedding = objectMapper.readValue(json, DeleteEmbedding.class);
        vectorStoreService.delete(deleteEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void chunkAdd(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        AddChunkEmbedding addChunkEmbedding = objectMapper.readValue(json, AddChunkEmbedding.class);
        vectorStoreService.chunkAdd(addChunkEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void chunkUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        UpdateChunkEmbedding updateChunkEmbedding = objectMapper.readValue(json, UpdateChunkEmbedding.class);
        vectorStoreService.chunkUpdate(updateChunkEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void chunkDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String json = requestToJson(req);
        DeleteChunkEmbedding deleteChunkEmbedding = objectMapper.readValue(json, DeleteChunkEmbedding.class);
        vectorStoreService.chunkDelete(deleteChunkEmbedding);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void resetBlockSize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        UserRagSetting userRagSetting = reqBodyToObj(req, UserRagSetting.class);
        Map<String, Object> result = new HashMap<>();
        try {
            uploadFileService.deleteTextBlockSize(userRagSetting);
            result.put("status", "success");
        } catch (SQLException e) {
            result.put("status", "failed");
        }
        responsePrint(resp, toJson(result));
    }

    private void updateTextBlockSize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        UserRagSetting userRagSetting = reqBodyToObj(req, UserRagSetting.class);
        Map<String, Object> result = new HashMap<>();
        try {
            uploadFileService.updateTextBlockSize(userRagSetting);
            result.put("status", "success");
        } catch (SQLException e) {
            result.put("status", "failed");
        }
        responsePrint(resp, toJson(result));
    }

    private void getTextBlockSize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String category = req.getParameter("category");
        String userId = req.getParameter("lagiUserId");

        Map<String, Object> map = new HashMap<>();
        List<UserRagSetting> result = null;
        try {
            result = uploadFileService.getTextBlockSize(category, userId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (result != null) {
            map.put("status", "success");
            map.put("data", result);
        } else {
            map.put("status", "failed");
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void search(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        ChatCompletionRequest request = reqBodyToObj(req, ChatCompletionRequest.class);
        List<IndexSearchData> indexSearchData = vectorDbService.searchByContext(request);
        Map<String, Object> result = new HashMap<>();
        if (indexSearchData == null || indexSearchData.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", indexSearchData);
        }
        responsePrint(resp, toJson(result));
    }

    private void searchByMetadata(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorSearchRequest request = reqBodyToObj(req, VectorSearchRequest.class);
        String text = request.getText();
        String category = request.getCategory();
        Map<String, Object> where = request.getWhere();
        List<IndexSearchData> indexSearchData = null;
        if (StrUtil.isNotBlank(text)) {
            indexSearchData = vectorStoreService.search(text, where, category);
        } else {
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setMessages(request.getMessages());
            chatCompletionRequest.setCategory(category);
            indexSearchData = vectorStoreService.searchByContext(chatCompletionRequest, where);
        }
        Map<String, Object> result = new HashMap<>();
        if (indexSearchData == null || indexSearchData.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", indexSearchData);
        }
        responsePrint(resp, toJson(result));
    }

    private void query(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        QueryCondition queryCondition = reqBodyToObj(req, QueryCondition.class);
        List<IndexRecord> recordList = vectorStoreService.query(queryCondition);
        Map<String, Object> result = new HashMap<>();
        if (recordList.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", recordList);
        }
        responsePrint(resp, toJson(result));
    }

    private void upsert(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorUpsertRequest vectorUpsertRequest = reqBodyToObj(req, VectorUpsertRequest.class);
        List<UpsertRecord> upsertRecords = vectorUpsertRequest.getData();
        String category = vectorUpsertRequest.getCategory();
        boolean isContextLinked = vectorUpsertRequest.getContextLinked();
        if (isContextLinked && upsertRecords.size() == 2) {
            long timestamp = Instant.now().toEpochMilli();
            UpsertRecord instructionRecord = upsertRecords.get(0);
            UpsertRecord outputRecord = upsertRecords.get(1);
            instructionRecord.getMetadata().put("seq", Long.toString(timestamp));
            outputRecord.getMetadata().put("seq", Long.toString(timestamp));
            String s1 = instructionRecord.getMetadata().get("filename");
            String s2 = outputRecord.getMetadata().get("filename");
            if (s1 == null && s2 == null) {
                instructionRecord.getMetadata().put("filename", "");
                outputRecord.getMetadata().put("filename", "");
            }
            VectorCacheLoader.put2L2(instructionRecord.getDocument().replaceAll("\n", ""), timestamp, outputRecord.getDocument());
        }
        vectorStoreService.upsertCustomVectors(upsertRecords, category, isContextLinked);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void deleteById(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        List<String> ids = vectorDeleteRequest.getIds();
        vectorStoreService.delete(ids, category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void deleteByMetadata(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        List<Map<String, String>> whereList = vectorDeleteRequest.getWhereList();
        vectorStoreService.deleteWhere(whereList, category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void deleteCollection(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        vectorStoreService.deleteCollection(category);
        uploadFileService.deleteUploadFile(category);
        bigdataService.delete(category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void listCollections(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        List<VectorCollection> collections = vectorStoreService.listCollections();
        Map<String, Object> result = new HashMap<>();
        if (collections.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", collections);
        }
        responsePrint(resp, toJson(result));
    }
}
