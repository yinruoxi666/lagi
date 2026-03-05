package ai.vector.impl;

import ai.common.pojo.VectorStoreConfig;
import ai.embedding.Embeddings;
import ai.vector.pojo.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChromaV2VectorStore extends BaseVectorStore {
    private static final int TIMEOUT = 60 * 3;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
            .build();
    private final Embeddings embeddingFunction;
    private final Map<String, Object> colMetadata;
    private final Map<String, String> collectionIdCache = new ConcurrentHashMap<>();

    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ChromaV2VectorStore(VectorStoreConfig config, Embeddings embeddingFunction) {
        this.config = config;
        this.embeddingFunction = embeddingFunction;
        this.colMetadata = new LinkedHashMap<>();
        this.colMetadata.put("hnsw:space", config.getMetric());
        this.colMetadata.put("embedding_function", embeddingFunction.getClass().getName());
    }

    @Override
    public void upsert(List<UpsertRecord> upsertRecords) {
        upsert(upsertRecords, this.config.getDefaultCategory());
    }

    @Override
    public void upsert(List<UpsertRecord> upsertRecords, String category) {
        if (upsertRecords == null || upsertRecords.isEmpty()) {
            return;
        }
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (UpsertRecord upsertRecord : upsertRecords) {
            documents.add(upsertRecord.getDocument());
            metadatas.add(toObjectMap(upsertRecord.getMetadata()));
            ids.add(upsertRecord.getId());
        }
        List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(documents);
        ChromaAddRequest request = ChromaAddRequest.builder()
                .ids(ids)
                .documents(documents)
                .metadatas(metadatas)
                .embeddings(embeddings)
                .build();
        postCollectionAction(category, "upsert", request);
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition) {
        String category = resolveCategory(queryCondition.getCategory());
        return queryInternal(queryCondition, category);
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition, String category) {
        return queryInternal(queryCondition, resolveCategory(category));
    }

    private List<IndexRecord> queryInternal(QueryCondition queryCondition, String category) {
        if (queryCondition.getText() == null || queryCondition.getText().trim().isEmpty()) {
            GetEmbedding getEmbedding = GetEmbedding.builder()
                    .category(category)
                    .where(queryCondition.getWhere())
                    .limit(queryCondition.getN())
                    .offset(0)
                    .whereDocument(queryCondition.getWhereDocument())
                    .build();
            return get(getEmbedding);
        }
        List<List<Float>> queryEmbeddings = this.embeddingFunction.createEmbedding(Collections.singletonList(queryCondition.getText()));
        ChromaQueryRequest request = ChromaQueryRequest.builder()
                .where(queryCondition.getWhere())
                .whereDocument(queryCondition.getWhereDocument())
                .queryEmbeddings(queryEmbeddings)
                .nResults(queryCondition.getN())
                .build();
        String json = withCollectionIdRetry(category, collectionId ->
                postJson(collectionRecordsPath(collectionId, "query"), toJson(request))
        );
        return parseQueryRecords(readTree(json));
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids) {
        return fetch(ids, this.config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids, String category) {
        GetEmbedding getEmbedding = GetEmbedding.builder()
                .category(resolveCategory(category))
                .ids(ids)
                .build();
        return get(getEmbedding);
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where) {
        return fetch(where, this.config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where, String category) {
        GetEmbedding getEmbedding = GetEmbedding.builder()
                .category(resolveCategory(category))
                .where(toObjectMap(where))
                .build();
        return get(getEmbedding);
    }

    @Override
    public List<IndexRecord> fetch(int limit, int offset, String category) {
        GetEmbedding getEmbedding = GetEmbedding.builder()
                .category(resolveCategory(category))
                .limit(limit)
                .offset(offset)
                .build();
        return get(getEmbedding);
    }

    @Override
    public void delete(List<String> ids) {
        delete(ids, this.config.getDefaultCategory());
    }

    @Override
    public void delete(List<String> ids, String category) {
        DeleteEmbedding deleteEmbedding = DeleteEmbedding.builder()
                .ids(ids)
                .category(resolveCategory(category))
                .build();
        delete(deleteEmbedding);
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList) {
        deleteWhere(whereList, this.config.getDefaultCategory());
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList, String category) {
        if (whereList == null || whereList.isEmpty()) {
            return;
        }
        String targetCategory = resolveCategory(category);
        for (Map<String, String> where : whereList) {
            DeleteEmbedding deleteEmbedding = DeleteEmbedding.builder()
                    .category(targetCategory)
                    .where(toObjectMap(where))
                    .build();
            delete(deleteEmbedding);
        }
    }

    @Override
    public void deleteCollection(String category) {
        String targetCategory = resolveCategory(category);
        String collectionId = findCollectionIdByName(targetCategory);
        if (!hasText(collectionId)) {
            collectionIdCache.remove(targetCategory);
            return;
        }
        deleteJson(collectionPath(collectionId));
        collectionIdCache.remove(targetCategory);
    }

    @Override
    public List<VectorCollection> listCollections() {
        List<VectorCollection> result = new ArrayList<>();
        JsonNode collectionNodes = listCollectionNodes();
        for (JsonNode node : collectionNodes) {
            String category = readText(node, "name", "collection_name");
            if (!hasText(category)) {
                continue;
            }
            String collectionId = readText(node, "id", "collection_id", "uuid");
            if (hasText(collectionId)) {
                collectionIdCache.put(category, collectionId);
            }
            int vectorCount = 0;
            if (hasText(collectionId)) {
                try {
                    vectorCount = countCollection(collectionId);
                } catch (RuntimeException ignored) {
                    vectorCount = 0;
                }
            }
            result.add(VectorCollection.builder()
                    .category(category)
                    .vectorCount(vectorCount)
                    .build());
        }
        return result;
    }

    @Override
    public List<IndexRecord> get(GetEmbedding getEmbedding) {
        String category = resolveCategory(getEmbedding.getCategory());
        GetEmbedding request = GetEmbedding.builder()
                .ids(getEmbedding.getIds())
                .where(getEmbedding.getWhere())
                .whereDocument(getEmbedding.getWhereDocument())
                .limit(getEmbedding.getLimit())
                .offset(getEmbedding.getOffset())
                .include(getEmbedding.getInclude())
                .build();
        String json = withCollectionIdRetry(category, collectionId ->
                postJson(collectionRecordsPath(collectionId, "get"), toJson(request))
        );
        return parseGetRecords(json);
    }

    @Override
    public void add(AddEmbedding addEmbedding) {
        String category = resolveCategory(addEmbedding.getCategory());
        ChromaAddRequest request = ChromaAddRequest.builder()
                .ids(new ArrayList<>())
                .documents(new ArrayList<>())
                .metadatas(new ArrayList<>())
                .embeddings(new ArrayList<>())
                .build();
        if (addEmbedding.getData() != null) {
            for (AddEmbedding.AddEmbeddingData data : addEmbedding.getData()) {
                if (!hasText(data.getId())) {
                    data.setId(UUID.randomUUID().toString().replace("-", ""));
                }
                request.getIds().add(data.getId());
                request.getDocuments().add(data.getDocument());
                request.getMetadatas().add(data.getMetadata());
                List<Float> embedding = data.getEmbedding();
                if ((embedding == null || embedding.isEmpty()) && hasText(data.getDocument())) {
                    List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(Collections.singletonList(data.getDocument()));
                    if (embeddings != null && !embeddings.isEmpty()) {
                        embedding = embeddings.get(0);
                    }
                }
                request.getEmbeddings().add(embedding);
            }
        }
        postCollectionAction(category, "add", request);
    }

    @Override
    public void update(UpdateEmbedding updateEmbedding) {
        String category = resolveCategory(updateEmbedding.getCategory());
        ChromaUpdateRequest request = ChromaUpdateRequest.builder()
                .ids(new ArrayList<>())
                .documents(new ArrayList<>())
                .metadatas(new ArrayList<>())
                .embeddings(new ArrayList<>())
                .build();
        if (updateEmbedding.getData() != null) {
            for (UpdateEmbedding.UpdateEmbeddingData data : updateEmbedding.getData()) {
                request.getIds().add(data.getId());
                request.getDocuments().add(data.getDocument());
                request.getMetadatas().add(data.getMetadata());
                List<Float> embedding = data.getEmbedding();
                if ((embedding == null || embedding.isEmpty()) && hasText(data.getDocument())) {
                    List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(Collections.singletonList(data.getDocument()));
                    if (embeddings != null && !embeddings.isEmpty()) {
                        embedding = embeddings.get(0);
                    }
                }
                request.getEmbeddings().add(embedding);
            }
        }
        postCollectionAction(category, "update", request);
    }

    @Override
    public void delete(DeleteEmbedding deleteEmbedding) {
        String category = resolveCategory(deleteEmbedding.getCategory());
        DeleteEmbedding request = DeleteEmbedding.builder()
                .ids(deleteEmbedding.getIds())
                .where(deleteEmbedding.getWhere())
                .whereDocument(deleteEmbedding.getWhereDocument())
                .build();
        postCollectionAction(category, "delete", request);
    }

    private void postCollectionAction(String category, String action, Object payload) {
        withCollectionIdRetry(resolveCategory(category), collectionId -> {
            postJson(collectionRecordsPath(collectionId, action), toJson(payload));
            return null;
        });
    }

    private <T> T withCollectionIdRetry(String category, CollectionOperation<T> operation) {
        String collectionId = getCollectionId(resolveCategory(category), true);
        try {
            return operation.call(collectionId);
        } catch (ChromaHttpException e) {
            if (e.getStatusCode() == 404) {
                collectionIdCache.remove(resolveCategory(category));
                String retryCollectionId = getCollectionId(resolveCategory(category), true);
                return operation.call(retryCollectionId);
            }
            throw e;
        }
    }

    private String getCollectionId(String category, boolean createIfMissing) {
        String cacheId = collectionIdCache.get(category);
        if (hasText(cacheId)) {
            return cacheId;
        }
        String collectionId = createIfMissing ? createOrGetCollection(category) : findCollectionIdByName(category);
        if (hasText(collectionId)) {
            collectionIdCache.put(category, collectionId);
        }
        if (!hasText(collectionId)) {
            throw new RuntimeException("Collection not found for category: " + category);
        }
        return collectionId;
    }

    private String createOrGetCollection(String category) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", category);
        request.put("get_or_create", true);
        request.put("metadata", colMetadata);
        String json = postJson(collectionsPath(), toJson(request));
        JsonNode root = unwrapDataNode(readTree(json));
        String collectionId = readText(root, "id", "collection_id", "uuid");
        if (hasText(collectionId)) {
            return collectionId;
        }
        return findCollectionIdByName(category);
    }

    private String findCollectionIdByName(String category) {
        JsonNode nodes = listCollectionNodes();
        for (JsonNode node : nodes) {
            String name = readText(node, "name", "collection_name");
            if (category.equals(name)) {
                return readText(node, "id", "collection_id", "uuid");
            }
        }
        return null;
    }

    private JsonNode listCollectionNodes() {
        String json = getJson(collectionsPath());
        JsonNode root = unwrapDataNode(readTree(json));
        if (root.isArray()) {
            return root;
        }
        JsonNode collections = root.path("collections");
        if (collections.isArray()) {
            return collections;
        }
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return data;
        }
        return OBJECT_MAPPER.createArrayNode();
    }

    private JsonNode unwrapDataNode(JsonNode root) {
        if (root != null && root.isObject()) {
            JsonNode data = root.path("data");
            if (!data.isMissingNode() && !data.isNull()) {
                return data;
            }
        }
        return root;
    }

    private int countCollection(String collectionId) {
        String json = getJson(collectionPath(collectionId) + "/count");
        JsonNode root = unwrapDataNode(readTree(json));
        if (root.isInt() || root.isLong()) {
            return root.asInt();
        }
        if (root.isTextual()) {
            return Integer.parseInt(root.asText());
        }
        JsonNode countNode = root.path("count");
        if (countNode.isInt() || countNode.isLong() || countNode.isTextual()) {
            return countNode.asInt();
        }
        return 0;
    }

    private List<IndexRecord> parseGetRecords(String json) {
        try {
            JsonNode root = unwrapDataNode(readTree(json));
            GetResult result = OBJECT_MAPPER.treeToValue(root, GetResult.class);
            return toIndexRecords(result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Chroma v2 get response", e);
        }
    }

    private List<IndexRecord> parseQueryRecords(JsonNode root) {
        root = unwrapDataNode(root);
        List<IndexRecord> result = new ArrayList<>();
        JsonNode idsNode = root.path("ids");
        if (!idsNode.isArray() || idsNode.size() == 0) {
            return result;
        }
        JsonNode docsNode = root.path("documents");
        JsonNode metadatasNode = root.path("metadatas");
        JsonNode distancesNode = root.path("distances");
        boolean nested = idsNode.get(0).isArray();
        if (nested) {
            for (int i = 0; i < idsNode.size(); i++) {
                JsonNode row = idsNode.get(i);
                if (!row.isArray()) {
                    continue;
                }
                for (int j = 0; j < row.size(); j++) {
                    result.add(buildQueryIndexRecord(idsNode, docsNode, metadatasNode, distancesNode, i, j, true));
                }
            }
            return result;
        }
        for (int i = 0; i < idsNode.size(); i++) {
            result.add(buildQueryIndexRecord(idsNode, docsNode, metadatasNode, distancesNode, 0, i, false));
        }
        return result;
    }

    private IndexRecord buildQueryIndexRecord(JsonNode idsNode,
                                              JsonNode docsNode,
                                              JsonNode metadatasNode,
                                              JsonNode distancesNode,
                                              int outer,
                                              int inner,
                                              boolean nested) {
        JsonNode idNode = getNodeValue(idsNode, outer, inner, nested);
        JsonNode docNode = getNodeValue(docsNode, outer, inner, nested);
        JsonNode metadataNode = getNodeValue(metadatasNode, outer, inner, nested);
        JsonNode distanceNode = getNodeValue(distancesNode, outer, inner, nested);
        Map<String, Object> metadata = null;
        if (metadataNode != null && metadataNode.isObject()) {
            metadata = OBJECT_MAPPER.convertValue(metadataNode, Map.class);
        }
        Float distance = null;
        if (distanceNode != null && distanceNode.isNumber()) {
            distance = (float) distanceNode.asDouble();
        }
        return IndexRecord.builder()
                .id(idNode == null || idNode.isNull() ? null : idNode.asText())
                .document(docNode == null || docNode.isNull() ? null : docNode.asText())
                .metadata(metadata)
                .distance(distance)
                .build();
    }

    private JsonNode getNodeValue(JsonNode node, int outer, int inner, boolean nested) {
        if (node == null || !node.isArray()) {
            return null;
        }
        if (nested) {
            if (outer >= node.size()) {
                return null;
            }
            JsonNode row = node.get(outer);
            if (row == null || !row.isArray() || inner >= row.size()) {
                return null;
            }
            return row.get(inner);
        }
        if (inner >= node.size()) {
            return null;
        }
        return node.get(inner);
    }

    private List<IndexRecord> toIndexRecords(GetResult result) {
        List<IndexRecord> records = new ArrayList<>();
        if (result == null || result.getIds() == null) {
            return records;
        }
        for (int i = 0; i < result.getIds().size(); i++) {
            records.add(IndexRecord.builder()
                    .id(result.getIds().get(i))
                    .document(getListItem(result.getDocuments(), i))
                    .metadata(getListItem(result.getMetadatas(), i))
                    .embeddings(getListItem(result.getEmbeddings(), i))
                    .build());
        }
        return records;
    }

    private <T> T getListItem(List<T> values, int index) {
        if (values == null || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    protected String postJson(String url, String jsonBody) {
        RequestBody body = RequestBody.create(
                jsonBody == null ? "{}" : jsonBody,
                MediaType.get("application/json; charset=utf-8")
        );
        Request.Builder builder = new Request.Builder().url(url).post(body);
        addHeaders(builder, buildHeaders());
        return execute(url, builder.build());
    }

    protected String getJson(String url) {
        Request.Builder builder = new Request.Builder().url(url).get();
        addHeaders(builder, buildHeaders());
        return execute(url, builder.build());
    }

    protected String deleteJson(String url) {
        Request.Builder builder = new Request.Builder().url(url).delete();
        addHeaders(builder, buildHeaders());
        return execute(url, builder.build());
    }

    protected Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (hasText(this.config.getApiKey())) {
            headers.put("x-chroma-token", this.config.getApiKey().trim());
        }
        return headers;
    }

    private String execute(String url, Request request) {
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ChromaHttpException(
                        response.code(),
                        url,
                        "Request failed with status " + response.code() + ", body=" + clip(body)
                );
            }
            return body;
        } catch (IOException e) {
            throw new RuntimeException("Request failed: " + url, e);
        }
    }

    private void addHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
    }

    private JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse response json", e);
        }
    }

    private String toJson(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request json", e);
        }
    }

    private String resolveCategory(String category) {
        return hasText(category) ? category : this.config.getDefaultCategory();
    }

    private String collectionsPath() {
        return buildBasePath() + "/collections";
    }

    private String collectionPath(String collectionId) {
        return collectionsPath() + "/" + encodePathSegment(collectionId);
    }

    private String collectionRecordsPath(String collectionId, String action) {
        return collectionPath(collectionId) + "/" + action;
    }

    protected String buildBasePath() {
        String tenant = hasText(this.config.getTenant()) ? this.config.getTenant() : "default_tenant";
        String database = hasText(this.config.getDatabase()) ? this.config.getDatabase() : "default_database";
        return trimTrailingSlash(this.config.getUrl())
                + "/api/v2/tenants/" + encodePathSegment(tenant)
                + "/databases/" + encodePathSegment(database);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String encodePathSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode path segment: " + value, e);
        }
    }

    private String readText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private Map<String, Object> toObjectMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String clip(String value) {
        if (value == null) {
            return "";
        }
        int max = 300;
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    @FunctionalInterface
    private interface CollectionOperation<T> {
        T call(String collectionId);
    }

    static class ChromaHttpException extends RuntimeException {
        private final int statusCode;
        private final String url;

        ChromaHttpException(int statusCode, String url, String message) {
            super(message);
            this.statusCode = statusCode;
            this.url = url;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getUrl() {
            return url;
        }
    }
}
