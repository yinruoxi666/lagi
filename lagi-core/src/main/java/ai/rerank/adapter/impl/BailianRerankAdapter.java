package ai.rerank.adapter.impl;

import ai.config.pojo.RerankConfig;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.RerankAdapterResult;
import ai.rerank.client.BailianRerankClient;

import java.util.ArrayList;
import java.util.List;

public class BailianRerankAdapter implements IRerankAdapter {
    static final int MAX_DOCUMENTS = 500;

    private BailianRerankClient client;

    public BailianRerankAdapter() {
    }

    BailianRerankAdapter(BailianRerankClient client) {
        this.client = client;
    }

    @Override
    public void initialize(RerankConfig config) {
        if (config == null || isMissing(config.getWorkspaceId()) || isMissing(config.getApiKey())) {
            throw new IllegalArgumentException("Bailian workspace_id and api_key are required");
        }
        client = new BailianRerankClient(config.getWorkspaceId().trim(), config.getApiKey().trim());
    }

    @Override
    public String getModelName() {
        return BailianRerankClient.MODEL_NAME;
    }

    @Override
    public RerankAdapterResult rerank(String query, List<String> documents) {
        if (client == null) {
            throw new IllegalStateException("Bailian rerank adapter is not initialized");
        }
        int requestSize = Math.min(documents.size(), MAX_DOCUMENTS);
        return client.rerank(query, new ArrayList<>(documents.subList(0, requestSize)));
    }

    private static boolean isMissing(String value) {
        return value == null || value.trim().isEmpty()
                || value.trim().toLowerCase().startsWith("your-");
    }
}
