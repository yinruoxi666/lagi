package ai.bigdata;


import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;
import ai.manager.BigdataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BigdataService {
    private static final Logger logger = LoggerFactory.getLogger(BigdataService.class);

    private IBigdata primaryAdapter() {
        IBigdata elastic = BigdataManager.getInstance().getBigdata("elastic");
        return elastic != null ? elastic : BigdataManager.getInstance().getBigdata();
    }

    public boolean upsert(TextIndexData data) {
        IBigdata adapter = primaryAdapter();
        if(adapter == null) {
            return false;
        }
        try {
            return adapter.upsert(data);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAvailable() {
        return primaryAdapter() != null;
    }

    public List<TermSearchHit> search(String keyword, String category, int topK) {
        return searchDetailed(keyword, category, topK).getHits();
    }

    public TermSearchResponse searchDetailed(String keyword, String category, int topK) {
        if (topK <= 0) {
            return TermSearchResponse.success("none", Collections.emptyList(), Collections.emptyList());
        }
        IBigdata primary = primaryAdapter();
        if (primary == null) {
            return TermSearchResponse.failure("none", "no_sparse_backend_configured");
        }
        TermSearchResponse primaryResponse;
        try {
            primaryResponse = primary.searchDetailed(keyword, category, topK);
        } catch (RuntimeException e) {
            primaryResponse = TermSearchResponse.failure(primary.getBackendName(), e.getClass().getSimpleName());
        }
        // A successful empty result is authoritative and must not trigger fallback.
        if (primaryResponse != null && primaryResponse.isSuccessful()) {
            return primaryResponse;
        }
        logger.warn("Sparse backend {} failed for category {}: {}",
                primary.getBackendName(), category,
                primaryResponse == null ? "null_response" : primaryResponse.getFailureReason());
        for (IBigdata fallback : BigdataManager.getInstance().getFallbackBigdatas("elastic")) {
            if (fallback == primary) {
                continue;
            }
            try {
                TermSearchResponse fallbackResponse = fallback.searchDetailed(keyword, category, topK);
                if (fallbackResponse != null && fallbackResponse.isSuccessful()) {
                    fallbackResponse.setFallbackUsed(true);
                    logger.warn("Sparse retrieval for category {} fell back to {}",
                            category, fallbackResponse.getBackend());
                    return fallbackResponse;
                }
            } catch (RuntimeException ignored) {
                // Try the next explicitly configured fallback.
            }
        }
        return primaryResponse == null
                ? TermSearchResponse.failure(primary.getBackendName(), "sparse_backend_failed")
                : primaryResponse;
    }

    public boolean delete(String category, List<String> ids) {
        IBigdata adapter = primaryAdapter();
        if (adapter == null || ids == null || ids.isEmpty()) {
            return false;
        }
        return adapter.delete(category, ids);
    }

    public boolean delete(String category) {
        IBigdata adapter = primaryAdapter();
        if (adapter == null) {
            return false;
        }
        return adapter.delete(category);
    }

    public Set<String> getIds(String keyword, String category) {
        IBigdata adapter = primaryAdapter();
        if (adapter == null) {
            return null;
        }
        return this.search(keyword, category, 1000).stream()
                .map(TermSearchHit::getId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
