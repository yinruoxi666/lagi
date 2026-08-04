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
        assertTrue(response.getResults().stream().allMatch(result -> result.getRerank() == null));
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
                        throw new IllegalStateException("mock timeout");
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
