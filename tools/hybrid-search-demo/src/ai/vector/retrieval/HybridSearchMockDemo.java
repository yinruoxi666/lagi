package ai.vector.retrieval;

import ai.bigdata.pojo.QueryKeywordScore;
import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;
import ai.vector.pojo.HybridMetadataSearchRequest;
import ai.vector.pojo.HybridMetadataSearchResponse;
import ai.vector.pojo.HybridMetadataSearchResult;
import ai.vector.pojo.IndexRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable mock demo for the complete explainable hybrid-search pipeline. */
public class HybridSearchMockDemo {
    public static void main(String[] args) throws Exception {
        Map<String, IndexRecord> records = mockRecords();
        HybridMetadataSearchEngine engine = new HybridMetadataSearchEngine(
                (query, category, where, whereDocument, topK) -> Arrays.asList(
                        records.get("doc-a"), records.get("doc-b")),
                (query, category, topK) -> TermSearchResponse.success(
                        "elasticsearch",
                        Arrays.asList(
                                QueryKeywordScore.builder().keyword("数据安全")
                                        .queryFrequency(1).documentFrequency(34L).score(3.68d).build(),
                                QueryKeywordScore.builder().keyword("管理制度")
                                        .queryFrequency(1).documentFrequency(71L).score(2.94d).build()),
                        Arrays.asList(
                                TermSearchHit.builder().id("doc-b").text(records.get("doc-b").getDocument())
                                        .score(8.72d).rank(1)
                                        .matchedKeywords(Arrays.asList("数据安全", "管理制度")).build(),
                                TermSearchHit.builder().id("doc-c").text(records.get("doc-c").getDocument())
                                        .score(6.10d).rank(2)
                                        .matchedKeywords(Collections.singletonList("管理制度")).build())),
                (ids, category, where, whereDocument) -> {
                    List<IndexRecord> resolved = new ArrayList<>();
                    for (String id : ids) {
                        if (records.containsKey(id)) {
                            resolved.add(records.get(id));
                        }
                    }
                    return resolved;
                },
                (query, model, documents) -> Arrays.asList(
                        new HybridMetadataSearchEngine.RerankOutcome(1, 0.95d),
                        new HybridMetadataSearchEngine.RerankOutcome(0, 0.90d),
                        new HybridMetadataSearchEngine.RerankOutcome(2, 0.80d)));

        HybridMetadataSearchResponse response = engine.search(
                HybridMetadataSearchRequest.builder()
                        .category("demo-knowledge")
                        .text("企业数据安全管理制度")
                        .where(Collections.singletonMap("tenant_id", "tenant-demo"))
                        .denseTopK(10)
                        .bm25TopK(10)
                        .fusionTopK(10)
                        .finalTopK(10)
                        .rrfK(60)
                        .denseWeight(1.0d)
                        .bm25Weight(1.0d)
                        .rerank(true)
                        .rerankModel("mock-reranker")
                        .build(),
                "default");

        verifyRequiredInformation(response);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("status", "success");
        envelope.put("data", response);
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
    }

    private static Map<String, IndexRecord> mockRecords() {
        Map<String, IndexRecord> records = new HashMap<>();
        records.put("doc-a", IndexRecord.builder()
                .id("doc-a")
                .document("企业数据安全管理办法与落地要求")
                .metadata(Collections.singletonMap("source", "policy-a.pdf"))
                .distance(0.12f)
                .build());
        records.put("doc-b", IndexRecord.builder()
                .id("doc-b")
                .document("数据安全管理制度包含访问控制和审计要求")
                .metadata(Collections.singletonMap("source", "policy-b.pdf"))
                .distance(0.22f)
                .build());
        records.put("doc-c", IndexRecord.builder()
                .id("doc-c")
                .document("管理制度的版本发布与复核流程")
                .metadata(Collections.singletonMap("source", "policy-c.pdf"))
                .build());
        return records;
    }

    private static void verifyRequiredInformation(HybridMetadataSearchResponse response) {
        require("elasticsearch".equals(response.getBm25Backend()), "BM25 backend missing");
        require("completed".equals(response.getBm25Status()), "BM25 status missing");
        require("completed".equals(response.getRerankStatus()), "rerank result missing");
        require(response.getQueryKeywords() != null && !response.getQueryKeywords().isEmpty(),
                "query keyword scores missing");
        require(response.getResults() != null && response.getResults().size() == 3,
                "expected three fused results");

        boolean hasHybridScore = false;
        boolean hasBm25 = false;
        boolean hasEmbeddingDistance = false;
        boolean hasRerank = false;
        for (HybridMetadataSearchResult result : response.getResults()) {
            hasHybridScore |= result.getHybridScore() != null && result.getHybridScore() > 0.0d;
            hasBm25 |= result.getBm25() != null && result.getBm25().getScore() != null
                    && result.getBm25().getMatchedKeywords() != null
                    && !result.getBm25().getMatchedKeywords().isEmpty();
            hasEmbeddingDistance |= result.getEmbedding() != null
                    && result.getEmbedding().getDistance() != null;
            hasRerank |= result.getRerank() != null && result.getRerank().getRank() != null
                    && result.getRerank().getScore() != null;
        }
        require(hasHybridScore, "hybrid score missing");
        require(hasBm25, "BM25 score or matched keywords missing");
        require(hasEmbeddingDistance, "embedding distance missing");
        require(hasRerank, "rerank rank or score missing");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
