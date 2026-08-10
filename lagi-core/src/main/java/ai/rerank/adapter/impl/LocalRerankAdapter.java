package ai.rerank.adapter.impl;

import ai.config.pojo.RerankConfig;
import ai.embedding.EmbeddingsUtil;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.RerankAdapterResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LocalRerankAdapter implements IRerankAdapter {
    private static final String MODEL_NAME = "local-embedding-rerank";

    @Override
    public void initialize(RerankConfig config) {
        // The local model path remains managed by EmbeddingGlobal for backward compatibility.
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public RerankAdapterResult rerank(String query, List<String> documents) {
        List<String> rankedDocuments = EmbeddingsUtil.rerank(query, documents);
        return RerankAdapterResult.builder()
                .id(UUID.randomUUID().toString())
                .model(MODEL_NAME)
                .processedDocuments(documents.size())
                .results(mapToOriginalIndices(documents, rankedDocuments))
                .build();
    }

    static List<RerankAdapterResult.Result> mapToOriginalIndices(List<String> originalDocuments,
                                                                  List<String> rankedDocuments) {
        Map<String, Deque<Integer>> originalIndices = new HashMap<>();
        for (int i = 0; i < originalDocuments.size(); i++) {
            originalIndices.computeIfAbsent(originalDocuments.get(i), ignored -> new ArrayDeque<>()).add(i);
        }

        List<RerankAdapterResult.Result> results = new ArrayList<>();
        if (rankedDocuments == null) {
            return results;
        }
        for (String rankedDocument : rankedDocuments) {
            Deque<Integer> indices = originalIndices.get(rankedDocument);
            if (indices == null || indices.isEmpty()) {
                throw new IllegalStateException("Local reranker returned a document outside the original input");
            }
            results.add(RerankAdapterResult.Result.builder()
                    .index(indices.removeFirst())
                    .build());
        }
        return results;
    }
}
