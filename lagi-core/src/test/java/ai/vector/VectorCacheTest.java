package ai.vector;

import ai.common.pojo.IndexSearchData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class VectorCacheTest {

    @Test
    void vectorLinkCacheDoesNotStoreQueryDistance() {
        IndexSearchData searchResult = indexSearchData(0.1F);

        List<IndexSearchData> cached = VectorLinkCacheData.copyWithDistance(
                Collections.singletonList(searchResult), null);

        assertNotSame(searchResult, cached.get(0));
        assertNull(cached.get(0).getDistance());
        assertEquals(0.1F, searchResult.getDistance());
    }

    @Test
    void cachedVectorLinkDataUsesOnlyTheCurrentQueryDistance() {
        List<IndexSearchData> cached = VectorLinkCacheData.copyWithDistance(
                Collections.singletonList(indexSearchData(0.1F)), null);

        List<IndexSearchData> secondQuery = VectorLinkCacheData.copyWithDistance(cached, 0.2F);
        List<IndexSearchData> withoutDistance = VectorLinkCacheData.copyWithDistance(cached, null);

        assertEquals(0.2F, secondQuery.get(0).getDistance());
        assertNull(withoutDistance.get(0).getDistance());
        assertNull(cached.get(0).getDistance());
        assertNotSame(cached.get(0), secondQuery.get(0));
    }

    private IndexSearchData indexSearchData(Float distance) {
        IndexSearchData data = new IndexSearchData();
        data.setId("vector-id");
        data.setText("cached context");
        data.setDistance(distance);
        return data;
    }
}
