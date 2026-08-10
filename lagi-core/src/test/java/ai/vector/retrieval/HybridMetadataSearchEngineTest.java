package ai.vector.retrieval;

import ai.bigdata.pojo.QueryKeywordScore;
import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;
import ai.vector.pojo.HybridMetadataSearchRequest;
import ai.vector.pojo.HybridMetadataSearchResponse;
import ai.vector.pojo.IndexRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridMetadataSearchEngineTest {

    @Test
    void returnsEveryRequiredExplainabilityField() {
        Map<String, IndexRecord> records = records();
        HybridMetadataSearchEngine engine = engine(records, false);

        HybridMetadataSearchResponse response = engine.search(request(), "default");

        assertEquals("elasticsearch", response.getBm25Backend());
        assertEquals("completed", response.getBm25Status());
        assertEquals("completed", response.getRerankStatus());
        assertEquals("数据安全", response.getQueryKeywords().get(0).getKeyword());
        assertEquals(3, response.getResults().size());
        assertTrue(response.getResults().stream().allMatch(result -> result.getHybridScore() != null));
        assertTrue(response.getResults().stream().anyMatch(result -> result.getBm25() != null
                && result.getBm25().getScore() != null
                && !result.getBm25().getMatchedKeywords().isEmpty()));
        assertTrue(response.getResults().stream().anyMatch(result -> result.getEmbedding() != null
                && result.getEmbedding().getDistance() != null));
        assertNotNull(response.getResults().get(0).getRerank());
        assertEquals(Integer.valueOf(1), response.getResults().get(0).getRerank().getRank());
    }

    @Test
    void preservesFusedResultsWhenRerankerFails() {
        HybridMetadataSearchResponse response = engine(records(), true).search(request(), "default");

        assertEquals("failed", response.getRerankStatus());
        assertEquals(3, response.getResults().size());
        assertEquals("doc-b", response.getResults().get(0).getId());
        assertTrue(response.getResults().stream().allMatch(result -> result.getRerank() != null));
        assertEquals(Integer.valueOf(1), response.getResults().get(0).getRerank().getRank());
        assertTrue(response.getResults().stream()
                .allMatch(result -> result.getRerank().getScore() == null));
    }

    @Test
    void resolvesQaTextBeforeReranking() {
        Map<String, Object> qaMetadata = new HashMap<>();
        qaMetadata.put("source", "qa");
        qaMetadata.put("parent_id", "");
        IndexRecord question = IndexRecord.builder().id("q-1").document("机场行李限额")
                .metadata(qaMetadata).distance(0.10f).build();
        IndexRecord ordinary = IndexRecord.builder().id("doc-1").document("普通文档")
                .metadata(Collections.emptyMap()).distance(0.20f).build();
        AtomicReference<List<String>> rerankDocuments = new AtomicReference<>();
        HybridMetadataSearchEngine engine = new HybridMetadataSearchEngine(
                (query, category, where, whereDocument, topK) -> Arrays.asList(question, ordinary),
                (query, category, topK) -> TermSearchResponse.success(
                        "elasticsearch", Collections.emptyList(), Collections.emptyList()),
                (ids, category, where, whereDocument) -> Collections.emptyList(),
                (query, model, documents) -> {
                    rerankDocuments.set(documents);
                    return Arrays.asList(
                            new HybridMetadataSearchEngine.RerankOutcome(0, 0.9d),
                            new HybridMetadataSearchEngine.RerankOutcome(1, 0.8d));
                },
                (id, text, metadata, category) -> "qa".equals(metadata.get("source"))
                        ? text + "$$$行李箱限额需要咨询柜台" : text);

        HybridMetadataSearchResponse response = engine.search(request(), "default");

        assertEquals("机场行李限额$$$行李箱限额需要咨询柜台",
                response.getResults().get(0).getText());
        assertEquals("机场行李限额$$$行李箱限额需要咨询柜台",
                rerankDocuments.get().get(0));
    }

    @Test
    void deduplicatesQuestionAndAnswerAfterQaAssembly() {
        Map<String, Object> questionMetadata = new HashMap<>();
        questionMetadata.put("source", "qa");
        questionMetadata.put("parent_id", "");
        Map<String, Object> answerMetadata = new HashMap<>();
        answerMetadata.put("source", "qa");
        answerMetadata.put("parent_id", "q-1");
        IndexRecord question = IndexRecord.builder().id("q-1").document("机场行李限额")
                .metadata(questionMetadata).distance(0.10f).build();
        IndexRecord answer = IndexRecord.builder().id("a-1").document("行李箱限额需要咨询柜台")
                .metadata(answerMetadata).distance(0.20f).build();
        HybridMetadataSearchEngine engine = new HybridMetadataSearchEngine(
                (query, category, where, whereDocument, topK) -> Arrays.asList(question, answer),
                (query, category, topK) -> TermSearchResponse.success(
                        "elasticsearch", Collections.emptyList(), Collections.emptyList()),
                (ids, category, where, whereDocument) -> Collections.emptyList(),
                null,
                (id, text, metadata, category) ->
                        "机场行李限额$$$行李箱限额需要咨询柜台");
        HybridMetadataSearchRequest request = request();
        request.setRerank(false);

        HybridMetadataSearchResponse response = engine.search(request, "default");

        assertEquals(1, response.getResults().size());
        assertEquals("q-1", response.getResults().get(0).getId());
        assertEquals("机场行李限额$$$行李箱限额需要咨询柜台",
                response.getResults().get(0).getText());
    }

    private static HybridMetadataSearchEngine engine(Map<String, IndexRecord> records,
                                                      boolean failRerank) {
        return new HybridMetadataSearchEngine(
                (query, category, where, whereDocument, topK) -> Arrays.asList(
                        records.get("doc-a"), records.get("doc-b")),
                (query, category, topK) -> TermSearchResponse.success(
                        "elasticsearch",
                        Collections.singletonList(QueryKeywordScore.builder()
                                .keyword("数据安全").queryFrequency(1)
                                .documentFrequency(34L).score(3.68d).build()),
                        Arrays.asList(
                                TermSearchHit.builder().id("doc-b").score(8.72d).rank(1)
                                        .matchedKeywords(Collections.singletonList("数据安全")).build(),
                                TermSearchHit.builder().id("doc-c").score(6.10d).rank(2)
                                        .matchedKeywords(Collections.singletonList("管理制度")).build())),
                (ids, category, where, whereDocument) -> Arrays.asList(
                        records.get("doc-b"), records.get("doc-c")),
                (query, model, documents) -> {
                    if (failRerank) {
                        throw new ExceptionInInitializerError("missing rerank model");
                    }
                    return Arrays.asList(
                            new HybridMetadataSearchEngine.RerankOutcome(1, 0.95d),
                            new HybridMetadataSearchEngine.RerankOutcome(0, 0.90d),
                            new HybridMetadataSearchEngine.RerankOutcome(2, 0.80d));
                });
    }

    private static HybridMetadataSearchRequest request() {
        return HybridMetadataSearchRequest.builder()
                .category("demo")
                .text("企业数据安全管理制度")
                .denseTopK(10)
                .bm25TopK(10)
                .fusionTopK(10)
                .finalTopK(10)
                .rerank(true)
                .build();
    }

    private static Map<String, IndexRecord> records() {
        Map<String, IndexRecord> records = new HashMap<>();
        records.put("doc-a", IndexRecord.builder().id("doc-a").document("dense a")
                .distance(0.12f).build());
        records.put("doc-b", IndexRecord.builder().id("doc-b").document("shared b")
                .distance(0.22f).build());
        records.put("doc-c", IndexRecord.builder().id("doc-c").document("sparse c").build());
        return records;
    }
}
