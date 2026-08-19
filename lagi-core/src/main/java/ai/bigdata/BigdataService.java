package ai.bigdata;


import ai.bigdata.pojo.TextIndexData;
import ai.manager.BigdataManager;
import ai.vector.diagnostics.VectorSearchPerformanceContext;

import java.util.List;
import java.util.Set;

public class BigdataService {
    private static final IBigdata adapter;

    static {
        adapter = BigdataManager.getInstance().getBigdata();
    }

    public boolean upsert(TextIndexData data) {
        if(adapter == null) {
            return false;
        }
        try {
            return adapter.upsert(data);
        } catch (Exception e) {
            return false;
        }
    }

    public List<TextIndexData> search(String keyword, String category) {
        return adapter.search(keyword, category);
    }

    public boolean delete(String category) {
        if (adapter == null) {
            return false;
        }
        return adapter.delete(category);
    }

    public Set<String> getIds(String keyword, String category) {
        if (adapter == null) {
            VectorSearchPerformanceContext.info("ES候选查询跳过：未配置Bigdata适配器，category={}", category);
            VectorSearchPerformanceContext.increment("es.skipped");
            return null;
        }
        long startedNanos = VectorSearchPerformanceContext.startTimer();
        try {
            Set<String> result = this.search(keyword, category).stream()
                    .map(TextIndexData::getId)
                    .collect(java.util.stream.Collectors.toSet());
            VectorSearchPerformanceContext.info("ES候选ID转换完成：category={}，ID数={}", category, result.size());
            return result;
        } finally {
            VectorSearchPerformanceContext.recordStage("es.getIds", "ES候选ID完整调用",
                    VectorSearchPerformanceContext.elapsedMillis(startedNanos),
                    VectorSearchPerformanceContext.HTTP_WARN_MS);
        }
    }
}
