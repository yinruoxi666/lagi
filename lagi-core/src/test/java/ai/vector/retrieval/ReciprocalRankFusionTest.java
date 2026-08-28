package ai.vector.retrieval;

import ai.bigdata.pojo.TermSearchHit;
import ai.vector.pojo.HybridSearchResult;
import ai.vector.pojo.IndexRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReciprocalRankFusionTest {

    @Test
    void boostsDocumentsReturnedByBothRetrievers() {
        List<IndexRecord> dense = Arrays.asList(record("dense-only"), record("shared"));
        List<TermSearchHit> sparse = Arrays.asList(hit("shared", 1, 4.2d), hit("sparse-only", 2, 2.1d));
        Map<String, IndexRecord> records = new HashMap<>();
        records.put("shared", record("shared"));
        records.put("sparse-only", record("sparse-only"));

        List<HybridSearchResult> result = ReciprocalRankFusion.fuse(
                dense, sparse, records, 60, 1.0d, 1.0d, 10);

        assertEquals(Arrays.asList("shared", "dense-only", "sparse-only"),
                Arrays.asList(result.get(0).getId(), result.get(1).getId(), result.get(2).getId()));
        assertEquals(Integer.valueOf(2), result.get(0).getDenseRank());
        assertEquals(Integer.valueOf(1), result.get(0).getSparseRank());
        assertEquals(Double.valueOf(4.2d), result.get(0).getTermScore());
    }

    @Test
    void appliesWeightsAndLimitWithoutComparingBackendScores() {
        List<IndexRecord> dense = Collections.singletonList(record("dense"));
        List<TermSearchHit> sparse = Collections.singletonList(hit("sparse", 1, 9999.0d));
        Map<String, IndexRecord> records = Collections.singletonMap("sparse", record("sparse"));

        List<HybridSearchResult> result = ReciprocalRankFusion.fuse(
                dense, sparse, records, 60, 2.0d, 0.5d, 1);

        assertEquals(1, result.size());
        assertEquals("dense", result.get(0).getId());
        assertNull(result.get(0).getTermScore());
        assertEquals(2.0d / 61.0d, result.get(0).getFusionScore(), 0.0000001d);
    }

    private static IndexRecord record(String id) {
        return IndexRecord.builder().id(id).document("document-" + id).build();
    }

    private static TermSearchHit hit(String id, int rank, double score) {
        return TermSearchHit.builder().id(id).rank(rank).score(score).build();
    }
}
