package ai.rerank.adapter.impl;

import ai.config.pojo.RerankConfig;
import ai.rerank.adapter.RerankAdapterResult;
import ai.rerank.client.BailianRerankClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BailianRerankAdapterTest {

    @Test
    void sendsAtMostFiveHundredDocuments() {
        AtomicInteger receivedSize = new AtomicInteger();
        BailianRerankClient client = new BailianRerankClient("workspace", "api-key") {
            @Override
            public RerankAdapterResult rerank(String query, List<String> documents) {
                receivedSize.set(documents.size());
                List<RerankAdapterResult.Result> results = new ArrayList<>();
                for (int i = 0; i < documents.size(); i++) {
                    results.add(RerankAdapterResult.Result.builder().index(i).build());
                }
                return RerankAdapterResult.builder()
                        .model(MODEL_NAME)
                        .processedDocuments(documents.size())
                        .results(results)
                        .build();
            }
        };
        BailianRerankAdapter adapter = new BailianRerankAdapter(client);
        List<String> documents = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            documents.add("document-" + i);
        }

        RerankAdapterResult result = adapter.rerank("query", documents);

        assertEquals(500, receivedSize.get());
        assertEquals(Integer.valueOf(500), result.getProcessedDocuments());
    }

    @Test
    void rejectsPlaceholderCredentials() {
        RerankConfig config = new RerankConfig();
        config.setWorkspaceId("your-workspace-id");
        config.setApiKey("your-api-key");

        assertThrows(IllegalArgumentException.class,
                () -> new BailianRerankAdapter().initialize(config));
    }
}
