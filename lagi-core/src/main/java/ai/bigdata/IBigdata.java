package ai.bigdata;


import ai.bigdata.pojo.TextIndexData;
import ai.bigdata.pojo.TermSearchHit;

import java.util.List;

public interface IBigdata {
    boolean upsert(TextIndexData data);

    List<TermSearchHit> search(String keyword, String category, int topK);

    boolean delete(String category, List<String> ids);

    boolean delete(String category);
}
