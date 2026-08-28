package ai.vector;

import ai.common.pojo.IndexSearchData;
import cn.hutool.core.bean.BeanUtil;

import java.util.ArrayList;
import java.util.List;

final class VectorLinkCacheData {

    private VectorLinkCacheData() {
    }

    static List<IndexSearchData> copyWithDistance(List<IndexSearchData> source, Float distance) {
        List<IndexSearchData> copies = new ArrayList<>(source.size());
        for (IndexSearchData data : source) {
            IndexSearchData copy = new IndexSearchData();
            BeanUtil.copyProperties(data, copy);
            copy.setDistance(distance);
            copies.add(copy);
        }
        return copies;
    }
}
