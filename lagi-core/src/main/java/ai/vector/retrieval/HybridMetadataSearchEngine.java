package ai.vector.retrieval;

import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;
import ai.vector.pojo.HybridMetadataSearchRequest;
import ai.vector.pojo.HybridMetadataSearchResponse;
import ai.vector.pojo.HybridMetadataSearchResult;
import ai.vector.pojo.HybridSearchResult;
import ai.vector.pojo.IndexRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Orchestrates dense recall, explainable BM25, weighted RRF and optional reranking. */
public class HybridMetadataSearchEngine {
    private final DenseRetriever denseRetriever;
    private final SparseRetriever sparseRetriever;
    private final RecordResolver recordResolver;
    private final RerankProvider rerankProvider;

    public HybridMetadataSearchEngine(DenseRetriever denseRetriever,
                                      SparseRetriever sparseRetriever,
                                      RecordResolver recordResolver,
                                      RerankProvider rerankProvider) {
        this.denseRetriever = Objects.requireNonNull(denseRetriever, "denseRetriever");
        this.sparseRetriever = Objects.requireNonNull(sparseRetriever, "sparseRetriever");
        this.recordResolver = Objects.requireNonNull(recordResolver, "recordResolver");
        this.rerankProvider = rerankProvider;
    }

    public HybridMetadataSearchResponse search(HybridMetadataSearchRequest request, String defaultCategory) {
        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Hybrid metadata search text is required");
        }
        String query = request.getText().trim();
        String category = request.getCategory() == null || request.getCategory().trim().isEmpty()
                ? defaultCategory : request.getCategory();
        int denseTopK = normalizeLimit(request.getDenseTopK(), 20, 1000);
        int bm25TopK = normalizeLimit(request.getBm25TopK(), 20, 1000);
        int fusionTopK = normalizeLimit(request.getFusionTopK(), 50, 1000);
        int finalTopK = normalizeLimit(request.getFinalTopK(), 10, fusionTopK);
        int rrfK = normalizeLimit(request.getRrfK(), 60, 10000);
        double denseWeight = normalizeWeight(request.getDenseWeight(), 1.0d);
        double bm25Weight = normalizeWeight(request.getBm25Weight(), 1.0d);

        List<IndexRecord> denseRecords = safeList(denseRetriever.retrieve(
                query, category, request.getWhere(), request.getWhereDocument(), denseTopK));
        TermSearchResponse sparseResponse = sparseRetriever.retrieve(
                query, category, Math.min(1000, bm25TopK * 5));
        if (sparseResponse == null) {
            sparseResponse = TermSearchResponse.failure("none", "null_sparse_response");
        }

        List<TermSearchHit> filteredSparseHits = new ArrayList<>();
        Map<String, IndexRecord> sparseRecordsById = new HashMap<>();
        if (sparseResponse.isSuccessful() && sparseResponse.getHits() != null) {
            List<String> sparseIds = sparseResponse.getHits().stream()
                    .map(TermSearchHit::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<IndexRecord> sparseRecords = safeList(recordResolver.resolve(
                    sparseIds, category, request.getWhere(), request.getWhereDocument()));
            for (IndexRecord record : sparseRecords) {
                if (record != null && record.getId() != null) {
                    sparseRecordsById.put(record.getId(), record);
                }
            }
            for (TermSearchHit hit : sparseResponse.getHits()) {
                if (hit != null && sparseRecordsById.containsKey(hit.getId())) {
                    hit.setRank(filteredSparseHits.size() + 1);
                    filteredSparseHits.add(hit);
                    if (filteredSparseHits.size() >= bm25TopK) {
                        break;
                    }
                }
            }
        }

        List<HybridSearchResult> fused = ReciprocalRankFusion.fuse(
                denseRecords, filteredSparseHits, sparseRecordsById,
                rrfK, denseWeight, bm25Weight, fusionTopK);
        List<HybridMetadataSearchResult> results = fused.stream()
                .map(this::toExplainableResult)
                .collect(Collectors.toList());

        String rerankStatus = "skipped";
        if ((request.getRerank() == null || request.getRerank())
                && rerankProvider != null && results.size() > 1) {
            try {
                results = applyRerank(query, request.getRerankModel(), results);
                rerankStatus = "completed";
            } catch (RuntimeException | LinkageError e) {
                results = applyRerankFallback(results);
                rerankStatus = "failed";
            }
        }
        if (results.size() > finalTopK) {
            results = new ArrayList<>(results.subList(0, finalTopK));
        }

        return HybridMetadataSearchResponse.builder()
                .query(query)
                .bm25Backend(sparseResponse.getBackend())
                .bm25FallbackUsed(sparseResponse.isFallbackUsed())
                .bm25Status(sparseResponse.isSuccessful() ? "completed" : "failed")
                .rerankStatus(rerankStatus)
                .queryKeywords(sparseResponse.getQueryKeywords())
                .results(results)
                .build();
    }

    private HybridMetadataSearchResult toExplainableResult(HybridSearchResult result) {
        HybridMetadataSearchResult.Bm25Evidence bm25 = result.getSparseRank() == null ? null
                : HybridMetadataSearchResult.Bm25Evidence.builder()
                .score(result.getTermScore())
                .rank(result.getSparseRank())
                .matchedKeywords(result.getMatchedKeywords())
                .build();
        HybridMetadataSearchResult.EmbeddingEvidence embedding = result.getDenseRank() == null ? null
                : HybridMetadataSearchResult.EmbeddingEvidence.builder()
                .distance(result.getDistance())
                .rank(result.getDenseRank())
                .build();
        return HybridMetadataSearchResult.builder()
                .id(result.getId())
                .text(result.getDocument())
                .metadata(result.getMetadata())
                .hybridScore(result.getFusionScore())
                .bm25(bm25)
                .embedding(embedding)
                .build();
    }

    private List<HybridMetadataSearchResult> applyRerank(String query, String model,
                                                          List<HybridMetadataSearchResult> candidates) {
        List<String> documents = candidates.stream()
                .map(result -> result.getText() == null ? "" : result.getText())
                .collect(Collectors.toList());
        List<RerankOutcome> outcomes = safeList(rerankProvider.rerank(query, model, documents));
        List<HybridMetadataSearchResult> reranked = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        int rank = 1;
        for (RerankOutcome outcome : outcomes) {
            if (outcome == null || outcome.getOriginalIndex() < 0
                    || outcome.getOriginalIndex() >= candidates.size()
                    || !used.add(outcome.getOriginalIndex())) {
                continue;
            }
            HybridMetadataSearchResult result = candidates.get(outcome.getOriginalIndex());
            result.setRerank(HybridMetadataSearchResult.RerankEvidence.builder()
                    .rank(rank++)
                    .score(outcome.getScore())
                    .build());
            reranked.add(result);
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (used.add(i)) {
                HybridMetadataSearchResult result = candidates.get(i);
                result.setRerank(HybridMetadataSearchResult.RerankEvidence.builder()
                        .rank(rank++)
                        .score(null)
                        .build());
                reranked.add(result);
            }
        }
        return reranked;
    }

    private List<HybridMetadataSearchResult> applyRerankFallback(
            List<HybridMetadataSearchResult> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setRerank(HybridMetadataSearchResult.RerankEvidence.builder()
                    .rank(i + 1)
                    .score(null)
                    .build());
        }
        return candidates;
    }

    private static int normalizeLimit(Integer value, int defaultValue, int maxValue) {
        return value == null ? Math.min(defaultValue, maxValue)
                : Math.max(1, Math.min(value, maxValue));
    }

    private static double normalizeWeight(Double value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 0.0d || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("Hybrid retrieval weights must be finite and non-negative");
        }
        return value;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public interface DenseRetriever {
        List<IndexRecord> retrieve(String query, String category, Map<String, Object> where,
                                   Map<String, Object> whereDocument, int topK);
    }

    public interface SparseRetriever {
        TermSearchResponse retrieve(String query, String category, int topK);
    }

    public interface RecordResolver {
        List<IndexRecord> resolve(List<String> ids, String category, Map<String, Object> where,
                                  Map<String, Object> whereDocument);
    }

    public interface RerankProvider {
        List<RerankOutcome> rerank(String query, String model, List<String> documents);
    }

    public static class RerankOutcome {
        private final int originalIndex;
        private final Double score;

        public RerankOutcome(int originalIndex, Double score) {
            this.originalIndex = originalIndex;
            this.score = score;
        }

        public int getOriginalIndex() {
            return originalIndex;
        }

        public Double getScore() {
            return score;
        }
    }
}
