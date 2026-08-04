package ai.bigdata;


import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;
import ai.bigdata.pojo.TermSearchResponse;

import java.util.List;

public interface IBigdata {
    boolean upsert(TextIndexData data);

    List<TermSearchHit> search(String keyword, String category, int topK);

    default TermSearchResponse searchDetailed(String keyword, String category, int topK) {
        return TermSearchResponse.success(
                getBackendName(), QueryKeywordAnalyzer.analyze(keyword), search(keyword, category, topK));
    }

    default String getBackendName() {
        return getClass().getSimpleName();
    }

    boolean delete(String category, List<String> ids);

    boolean delete(String category);
}
