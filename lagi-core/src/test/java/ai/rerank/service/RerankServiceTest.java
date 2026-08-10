package ai.rerank.service;

import ai.config.pojo.RerankConfig;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.RerankAdapterResult;
import ai.rerank.pojo.RerankRequest;
import ai.rerank.pojo.RerankResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RerankServiceTest {

    @Test
    void mapsAdapterIndicesAndScoresBackToOriginalDocuments() {
        IRerankAdapter adapter = adapterReturning(3, Arrays.asList(
                result(2, 0.9d), result(0, 0.8d), result(1, 0.7d)));

        RerankResponse response = new RerankService(adapter).rerank(request(
                Arrays.asList("same", "other", "same")));

        assertEquals(Arrays.asList(2, 0, 1), resultIndices(response));
        assertEquals("same", response.getResults().get(0).getDocument().getText());
        assertEquals(Double.valueOf(0.9d), response.getResults().get(0).getRelevanceScore());
        assertEquals("test-rerank", response.getModel());
    }

    @Test
    void filtersBlankDocumentsAndAppendsThemInOriginalOrder() {
        AtomicReference<List<String>> receivedDocuments = new AtomicReference<>();
        IRerankAdapter adapter = new TestAdapter() {
            @Override
            public RerankAdapterResult rerank(String query, List<String> documents) {
                receivedDocuments.set(documents);
                return adapterResult(2, Arrays.asList(result(1, 0.9d), result(0, 0.8d)));
            }
        };

        RerankResponse response = new RerankService(adapter).rerank(request(
                Arrays.asList("one", " ", null, "two")));

        assertEquals(Arrays.asList("one", "two"), receivedDocuments.get());
        assertEquals(Arrays.asList(3, 0, 1, 2), resultIndices(response));
        assertNull(response.getResults().get(2).getRelevanceScore());
        assertNull(response.getResults().get(3).getRelevanceScore());
    }

    @Test
    void appendsCandidatesNotProcessedByAdapter() {
        List<String> documents = new ArrayList<>();
        List<RerankAdapterResult.Result> ranked = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            documents.add("document-" + i);
            if (i < 500) {
                ranked.add(result(499 - i, (double) i));
            }
        }

        RerankResponse response = new RerankService(adapterReturning(500, ranked))
                .rerank(request(documents));

        assertEquals(501, response.getResults().size());
        assertEquals(Integer.valueOf(499), response.getResults().get(0).getIndex());
        assertEquals(Integer.valueOf(500), response.getResults().get(500).getIndex());
        assertNull(response.getResults().get(500).getRelevanceScore());
    }

    @Test
    void rejectsDuplicateAdapterIndices() {
        IRerankAdapter adapter = adapterReturning(2,
                Arrays.asList(result(0, 0.9d), result(0, 0.8d)));

        assertThrows(IllegalStateException.class,
                () -> new RerankService(adapter).rerank(request(Arrays.asList("one", "two"))));
    }

    @Test
    void doesNotCallAdapterForAllBlankDocuments() {
        AtomicBoolean called = new AtomicBoolean();
        IRerankAdapter adapter = new TestAdapter() {
            @Override
            public RerankAdapterResult rerank(String query, List<String> documents) {
                called.set(true);
                return adapterResult(0, Collections.emptyList());
            }
        };

        RerankResponse response = new RerankService(adapter)
                .rerank(request(Arrays.asList("", " ", null)));

        assertFalse(called.get());
        assertEquals(Arrays.asList(0, 1, 2), resultIndices(response));
    }

    private static IRerankAdapter adapterReturning(int processedDocuments,
                                                    List<RerankAdapterResult.Result> results) {
        return new TestAdapter() {
            @Override
            public RerankAdapterResult rerank(String query, List<String> documents) {
                return adapterResult(processedDocuments, results);
            }
        };
    }

    private static RerankAdapterResult adapterResult(int processedDocuments,
                                                     List<RerankAdapterResult.Result> results) {
        return RerankAdapterResult.builder()
                .id("request-id")
                .model("test-rerank")
                .totalTokens(12)
                .processedDocuments(processedDocuments)
                .results(results)
                .build();
    }

    private static RerankAdapterResult.Result result(int index, double score) {
        return RerankAdapterResult.Result.builder()
                .index(index)
                .relevanceScore(score)
                .build();
    }

    private static RerankRequest request(List<String> documents) {
        return RerankRequest.builder()
                .model("ignored-request-model")
                .query("query")
                .documents(documents)
                .build();
    }

    private static List<Integer> resultIndices(RerankResponse response) {
        List<Integer> indices = new ArrayList<>();
        for (RerankResponse.RerankResult result : response.getResults()) {
            indices.add(result.getIndex());
        }
        return indices;
    }

    private abstract static class TestAdapter implements IRerankAdapter {
        @Override
        public void initialize(RerankConfig config) {
        }

        @Override
        public String getModelName() {
            return "test-rerank";
        }
    }
}
