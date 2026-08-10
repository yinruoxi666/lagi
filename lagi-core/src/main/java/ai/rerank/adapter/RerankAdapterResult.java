package ai.rerank.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankAdapterResult {
    private String id;
    private String model;
    private Integer totalTokens;
    private Integer processedDocuments;
    private List<Result> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private Integer index;
        private Double relevanceScore;
    }
}
