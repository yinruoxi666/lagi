package ai.bigdata.impl;

import ai.bigdata.IBigdata;
import ai.bigdata.QueryKeywordAnalyzer;
import ai.bigdata.pojo.QueryKeywordScore;
import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;
import ai.config.pojo.BigdataConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ElasticSearchAdapter implements IBigdata {
    private static final String QUERY_ANALYZER = "smartcn";
    private final ElasticsearchClient client;
    private final RestClient restClient;
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchAdapter.class);

    public ElasticSearchAdapter(BigdataConfig config) {
        RestClientBuilder builder;
        if (config.getUsername() == null || config.getPassword() == null) {
            builder = RestClient.builder(new HttpHost(config.getHost(), config.getPort()));
        } else {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));
            builder = RestClient.builder(new HttpHost(config.getHost(), config.getPort())).setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        RestClient restClient = builder.build();
        this.restClient = restClient;
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    @Override
    public boolean upsert(TextIndexData data) {
        boolean result = false;
        try {
            String indexName = toIndexName(data.getCategory());
            ensureIndex(indexName);
            IndexResponse response = client.index(i -> i.index(indexName).id(data.getId()).document(data));
            if (response.result().equals(Result.Created) || response.result().equals(Result.Updated)) {
                result = true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private void ensureIndex(String indexName) throws IOException {
        if (client.indices().exists(i -> i.index(indexName)).value()) {
            return;
        }
        try {
            client.indices().create(c -> c.index(indexName).mappings(m -> m
                    .properties("id", p -> p.keyword(k -> k))
                    .properties("category", p -> p.keyword(k -> k))
                    .properties("text", p -> p.text(t -> t
                            .analyzer("cjk")
                            .searchAnalyzer("cjk")))));
        } catch (ElasticsearchException e) {
            // Another writer may create the same category index concurrently.
            if (!client.indices().exists(i -> i.index(indexName)).value()) {
                throw e;
            }
        }
    }

    @Override
    public List<TermSearchHit> search(String keyword, String category, int topK) {
        TermSearchResponse response = searchDetailed(keyword, category, topK);
        return response.isSuccessful() ? response.getHits() : new ArrayList<>();
    }

    @Override
    public TermSearchResponse searchDetailed(String keyword, String category, int topK) {
        if (keyword == null || keyword.trim().isEmpty() || category == null || topK <= 0) {
            return TermSearchResponse.success(getBackendName(), Collections.emptyList(), Collections.emptyList());
        }
        String indexName = toIndexName(category);
        List<QueryKeywordScore> queryKeywords = analyzeQuery(keyword, category, indexName);
        // 检查索引是否存在
        BooleanResponse indexExistsResponse = null;
        try {
            indexExistsResponse = client.indices().exists(i -> i.index(indexName));
        } catch (IOException | ElasticsearchException e) {
            logger.error("Error while checking index existence", e);
            return TermSearchResponse.failure(getBackendName(), "index_check_failed");
        }

        if (!indexExistsResponse.value()) {
            logger.warn("Index {} does not exist", category);
            return TermSearchResponse.failure(getBackendName(), "index_not_found");
        }
        SearchResponse<TextIndexData> searchResponse = null;
        try {
            searchResponse = client.search(s -> s.index(indexName)
                    .size(topK)
                    .query(q -> q.bool(b -> b
                            .should(sq -> sq.match(m -> m.field("text").query(keyword)
                                    .analyzer(QUERY_ANALYZER)))
                            .should(sq -> sq.matchPhrase(m -> m.field("text").query(keyword)
                                    .analyzer(QUERY_ANALYZER).boost(2.0f)))
                            .minimumShouldMatch("1"))), TextIndexData.class);
        } catch (IOException | ElasticsearchException e) {
            logger.error("Error while searching", e);
            return TermSearchResponse.failure(getBackendName(), "search_failed");
        }
        if (searchResponse == null) {
            return TermSearchResponse.failure(getBackendName(), "empty_search_response");
        }
        List<Hit<TextIndexData>> hits = searchResponse.hits().hits();
        List<TermSearchHit> result = new ArrayList<>();
        int rank = 1;
        for (Hit<TextIndexData> hit : hits) {
            TextIndexData source = hit.source();
            result.add(TermSearchHit.builder()
                    .id(hit.id())
                    .text(source == null ? null : source.getText())
                    .score(hit.score())
                    .rank(rank++)
                    .matchedKeywords(findMatchedKeywords(
                            source == null ? null : source.getText(), queryKeywords))
                    .build());
        }
        return TermSearchResponse.success(getBackendName(), queryKeywords, result);
    }

    @Override
    public String getBackendName() {
        return "elasticsearch";
    }

    private List<QueryKeywordScore> analyzeQuery(String query, String category, String indexName) {
        try {
            Request request = new Request("POST", "/" + indexName + "/_termvectors");
            JsonObject body = new JsonObject();
            JsonObject document = new JsonObject();
            document.addProperty("text", query);
            body.add("doc", document);
            com.google.gson.JsonArray fields = new com.google.gson.JsonArray();
            fields.add("text");
            body.add("fields", fields);
            JsonObject perFieldAnalyzer = new JsonObject();
            perFieldAnalyzer.addProperty("text", QUERY_ANALYZER);
            body.add("per_field_analyzer", perFieldAnalyzer);
            body.addProperty("term_statistics", true);
            body.addProperty("field_statistics", true);
            body.addProperty("positions", false);
            body.addProperty("offsets", false);
            body.addProperty("payloads", false);
            request.setJsonEntity(body.toString());

            Response response = restClient.performRequest(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textField = root.getAsJsonObject("term_vectors").getAsJsonObject("text");
            JsonObject fieldStatistics = textField.getAsJsonObject("field_statistics");
            long documentCount = fieldStatistics == null || !fieldStatistics.has("doc_count")
                    ? 0L : fieldStatistics.get("doc_count").getAsLong();
            JsonObject terms = textField.getAsJsonObject("terms");
            List<QueryKeywordScore> result = new ArrayList<>();
            if (terms != null) {
                for (Map.Entry<String, JsonElement> entry : terms.entrySet()) {
                    JsonObject statistics = entry.getValue().getAsJsonObject();
                    int queryFrequency = statistics.has("term_freq")
                            ? statistics.get("term_freq").getAsInt() : 1;
                    long documentFrequency = statistics.has("doc_freq")
                            ? statistics.get("doc_freq").getAsLong() : 0L;
                    double idf = Math.log(1.0d + (Math.max(0L, documentCount - documentFrequency) + 0.5d)
                            / (documentFrequency + 0.5d));
                    result.add(QueryKeywordScore.builder()
                            .keyword(entry.getKey())
                            .queryFrequency(queryFrequency)
                            .documentFrequency(documentFrequency)
                            .score(queryFrequency * idf)
                            .build());
                }
            }
            result.sort(Comparator.comparing(QueryKeywordScore::getScore).reversed()
                    .thenComparing(QueryKeywordScore::getKeyword));
            return result;
        } catch (Exception e) {
            logger.warn("Failed to obtain Elasticsearch query term statistics for category {}: {}",
                    category, e.getMessage());
            return QueryKeywordAnalyzer.analyze(query);
        }
    }

    private List<String> findMatchedKeywords(String text, List<QueryKeywordScore> queryKeywords) {
        if (text == null || queryKeywords == null || queryKeywords.isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (QueryKeywordScore keyword : queryKeywords) {
            if (keyword.getKeyword() != null
                    && normalized.contains(keyword.getKeyword().toLowerCase(Locale.ROOT))) {
                matched.add(keyword.getKeyword());
            }
        }
        return matched;
    }

    @Override
    public boolean delete(String category, List<String> ids) {
        if (category == null || ids == null || ids.isEmpty()) {
            return false;
        }
        String indexName = toIndexName(category);
        try {
            BulkResponse response = client.bulk(b -> {
                for (String id : ids) {
                    b.operations(op -> op.delete(d -> d.index(indexName).id(id)));
                }
                return b;
            });
            if (!response.errors()) {
                return true;
            }
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    logger.error("Error deleting term index id {}: {}", item.id(), item.error().reason());
                }
            }
        } catch (IOException | ElasticsearchException e) {
            logger.error("Error deleting term index ids from category {}", category, e);
        }
        return false;
    }

    @Override
    public boolean delete(String category) {
        boolean result = false;
        try {
            DeleteIndexResponse response = client.indices().delete(i -> i.index(toIndexName(category)));
            result = response.acknowledged();
        } catch (IOException | ElasticsearchException e) {
            logger.error("Error while deleting", e);
        }
        return result;
    }

    /**
     * Elasticsearch index names must be lowercase, while vector-store categories are
     * case-sensitive and may contain uppercase characters. Keep a readable lowercase
     * prefix and append a digest of the original category so case-only variants cannot
     * collide.
     */
    static String toIndexName(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        String original = category.trim();
        String readable = original.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-");
        if (readable.isEmpty()) {
            readable = "category";
        }
        if (readable.length() > 200) {
            readable = readable.substring(0, 200);
        }
        return "lagi-" + readable + "-" + sha256Prefix(original);
    }

    private static String sha256Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
