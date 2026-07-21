package ai.rerank.service;

import ai.embedding.EmbeddingsUtil;
import ai.rerank.pojo.RerankRequest;
import ai.rerank.pojo.RerankResponse;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RerankService {
    public RerankResponse rerank(RerankRequest rerankRequest) {
        if (rerankRequest == null || rerankRequest.getQuery() == null
                || rerankRequest.getDocuments() == null) {
            throw new IllegalArgumentException("Rerank query and documents are required");
        }
        String query = rerankRequest.getQuery();
        List<String> docs = rerankRequest.getDocuments();
        List<String> rankedChunks = EmbeddingsUtil.rerank(query, docs);
        RerankResponse rerankResponse = new RerankResponse();
        rerankResponse.setId(UUID.randomUUID().toString());
        rerankResponse.setModel(rerankRequest.getModel());
        rerankResponse.setResults(buildResults(docs, rankedChunks));
        return rerankResponse;
    }

    static List<RerankResponse.RerankResult> buildResults(List<String> originalDocuments,
                                                           List<String> rankedDocuments) {
        Map<String, Deque<Integer>> originalIndices = new HashMap<>();
        for (int i = 0; i < originalDocuments.size(); i++) {
            originalIndices.computeIfAbsent(originalDocuments.get(i), ignored -> new ArrayDeque<>()).add(i);
        }

        List<RerankResponse.RerankResult> results = new ArrayList<>();
        if (rankedDocuments == null) {
            return results;
        }
        for (String rankedDocument : rankedDocuments) {
            Deque<Integer> indices = originalIndices.get(rankedDocument);
            if (indices == null || indices.isEmpty()) {
                throw new IllegalStateException("Reranker returned a document outside the original input");
            }
            RerankResponse.RerankResult result = new RerankResponse.RerankResult();
            result.setIndex(indices.removeFirst());
            RerankResponse.Document document = new RerankResponse.Document();
            document.setText(rankedDocument);
            result.setDocument(document);
            results.add(result);
        }
        return results;
    }
}
