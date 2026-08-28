package ai.bigdata;


import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;
import ai.manager.BigdataManager;

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

    public boolean isAvailable() {
        return adapter != null;
    }

    public List<TermSearchHit> search(String keyword, String category, int topK) {
        if (adapter == null || topK <= 0) {
            return java.util.Collections.emptyList();
        }
        return adapter.search(keyword, category, topK);
    }

    public boolean delete(String category, List<String> ids) {
        if (adapter == null || ids == null || ids.isEmpty()) {
            return false;
        }
        return adapter.delete(category, ids);
    }

    public boolean delete(String category) {
        if (adapter == null) {
            return false;
        }
        return adapter.delete(category);
    }

    public Set<String> getIds(String keyword, String category) {
        if (adapter == null) {
            return null;
        }
        return this.search(keyword, category, 1000).stream()
                .map(TermSearchHit::getId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
