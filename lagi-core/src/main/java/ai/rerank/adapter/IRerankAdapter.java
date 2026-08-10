package ai.rerank.adapter;

import ai.config.pojo.RerankConfig;

import java.util.List;

public interface IRerankAdapter {
    void initialize(RerankConfig config);

    String getModelName();

    RerankAdapterResult rerank(String query, List<String> documents);
}
