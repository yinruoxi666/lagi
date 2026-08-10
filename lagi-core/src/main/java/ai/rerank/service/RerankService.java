package ai.rerank.service;

import ai.manager.RerankManager;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.RerankAdapterResult;
import ai.rerank.pojo.RerankRequest;
import ai.rerank.pojo.RerankResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class RerankService {
    private final Supplier<IRerankAdapter> adapterSupplier;

    public RerankService() {
        this(() -> RerankManager.getInstance().getAdapter());
    }

    RerankService(IRerankAdapter adapter) {
        this(() -> adapter);
    }

    private RerankService(Supplier<IRerankAdapter> adapterSupplier) {
        this.adapterSupplier = adapterSupplier;
    }

    public RerankResponse rerank(RerankRequest rerankRequest) {
        if (rerankRequest == null || isBlank(rerankRequest.getQuery())
                || rerankRequest.getDocuments() == null) {
            throw new IllegalArgumentException("Rerank query and documents are required");
        }

        IRerankAdapter adapter = adapterSupplier.get();
        if (adapter == null) {
            throw new IllegalStateException("Rerank adapter is not available; check functions.rerank configuration");
        }

        List<String> originalDocuments = rerankRequest.getDocuments();
        List<String> effectiveDocuments = new ArrayList<>();
        List<Integer> effectiveOriginalIndices = new ArrayList<>();
        for (int i = 0; i < originalDocuments.size(); i++) {
            String document = originalDocuments.get(i);
            if (!isBlank(document)) {
                effectiveDocuments.add(document);
                effectiveOriginalIndices.add(i);
            }
        }

        RerankAdapterResult adapterResult = effectiveDocuments.isEmpty()
                ? emptyResult(adapter)
                : adapter.rerank(rerankRequest.getQuery(), effectiveDocuments);
        validateAdapterResult(adapterResult, effectiveDocuments.size());

        RerankResponse rerankResponse = new RerankResponse();
        rerankResponse.setId(isBlank(adapterResult.getId())
                ? UUID.randomUUID().toString() : adapterResult.getId());
        rerankResponse.setModel(isBlank(adapterResult.getModel())
                ? adapter.getModelName() : adapterResult.getModel());
        if (adapterResult.getTotalTokens() != null) {
            rerankResponse.setUsage(RerankResponse.Usage.builder()
                    .totalTokens(adapterResult.getTotalTokens())
                    .build());
        }
        rerankResponse.setResults(buildResults(
                originalDocuments, effectiveOriginalIndices, adapterResult.getResults()));
        return rerankResponse;
    }

    private static List<RerankResponse.RerankResult> buildResults(
            List<String> originalDocuments,
            List<Integer> effectiveOriginalIndices,
            List<RerankAdapterResult.Result> adapterResults) {
        List<RerankResponse.RerankResult> results = new ArrayList<>();
        Set<Integer> usedOriginalIndices = new HashSet<>();
        for (RerankAdapterResult.Result adapterResult : adapterResults) {
            int originalIndex = effectiveOriginalIndices.get(adapterResult.getIndex());
            usedOriginalIndices.add(originalIndex);
            results.add(toResponseResult(originalIndex, originalDocuments.get(originalIndex),
                    adapterResult.getRelevanceScore()));
        }
        for (int i = 0; i < originalDocuments.size(); i++) {
            if (usedOriginalIndices.add(i)) {
                results.add(toResponseResult(i, originalDocuments.get(i), null));
            }
        }
        return results;
    }

    private static RerankResponse.RerankResult toResponseResult(int index, String text, Double score) {
        return RerankResponse.RerankResult.builder()
                .index(index)
                .document(RerankResponse.Document.builder().text(text).build())
                .relevanceScore(score)
                .build();
    }

    private static void validateAdapterResult(RerankAdapterResult adapterResult,
                                              int availableDocuments) {
        if (adapterResult == null || adapterResult.getResults() == null
                || adapterResult.getProcessedDocuments() == null) {
            throw new IllegalStateException("Rerank adapter returned an incomplete response");
        }
        int processedDocuments = adapterResult.getProcessedDocuments();
        if (processedDocuments < 0 || processedDocuments > availableDocuments
                || adapterResult.getResults().size() != processedDocuments) {
            throw new IllegalStateException("Rerank adapter returned an invalid result count");
        }
        Set<Integer> indices = new HashSet<>();
        for (RerankAdapterResult.Result result : adapterResult.getResults()) {
            if (result == null || result.getIndex() == null
                    || result.getIndex() < 0 || result.getIndex() >= processedDocuments
                    || !indices.add(result.getIndex())) {
                throw new IllegalStateException("Rerank adapter returned an invalid result index");
            }
        }
    }

    private static RerankAdapterResult emptyResult(IRerankAdapter adapter) {
        return RerankAdapterResult.builder()
                .id(UUID.randomUUID().toString())
                .model(adapter.getModelName())
                .processedDocuments(0)
                .results(new ArrayList<>())
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
