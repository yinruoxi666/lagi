package ai.rerank.service;

import ai.embedding.EmbeddingsUtil;
import ai.rerank.pojo.RerankRequest;
import ai.rerank.pojo.RerankResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RerankService {
    public RerankResponse rerank(RerankRequest rerankRequest) {
        String query = rerankRequest.getQuery();
        List<String> docs = rerankRequest.getDocuments();
        List<String> rankedChunks = EmbeddingsUtil.rerank(query, docs);
        RerankResponse rerankResponse = new RerankResponse();
        rerankResponse.setId(UUID.randomUUID().toString());
        List<RerankResponse.RerankResult> results = new ArrayList<>();
        for (int i = 0; i < rankedChunks.size(); i++) {
            RerankResponse.RerankResult result = new RerankResponse.RerankResult();
            result.setIndex(i);
            RerankResponse.Document document = new RerankResponse.Document();
            document.setText(rankedChunks.get(i));
            result.setDocument(document);
            results.add(result);
        }
        rerankResponse.setResults(results);
        return rerankResponse;
    }
}
