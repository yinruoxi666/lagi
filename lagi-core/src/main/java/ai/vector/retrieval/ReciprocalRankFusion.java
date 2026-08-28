package ai.vector.retrieval;

import ai.bigdata.pojo.TermSearchHit;
import ai.vector.pojo.HybridSearchResult;
import ai.vector.pojo.IndexRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combines independently ranked dense and sparse results using weighted RRF. */
public final class ReciprocalRankFusion {
    private ReciprocalRankFusion() {
    }

    public static List<HybridSearchResult> fuse(List<IndexRecord> denseRecords,
                                                 List<TermSearchHit> sparseHits,
                                                 Map<String, IndexRecord> recordsById,
                                                 int rrfK,
                                                 double denseWeight,
                                                 double sparseWeight,
                                                 int limit) {
        Map<String, HybridSearchResult> candidates = new LinkedHashMap<>();

        if (denseRecords != null) {
            for (int i = 0; i < denseRecords.size(); i++) {
                IndexRecord record = denseRecords.get(i);
                if (record == null || record.getId() == null) {
                    continue;
                }
                int rank = i + 1;
                HybridSearchResult result = fromRecord(record);
                result.setDenseRank(rank);
                result.setFusionScore(denseWeight / (rrfK + rank));
                candidates.put(record.getId(), result);
            }
        }

        if (sparseHits != null) {
            for (int i = 0; i < sparseHits.size(); i++) {
                TermSearchHit hit = sparseHits.get(i);
                if (hit == null || hit.getId() == null) {
                    continue;
                }
                int rank = hit.getRank() == null || hit.getRank() <= 0 ? i + 1 : hit.getRank();
                HybridSearchResult result = candidates.get(hit.getId());
                if (result == null) {
                    IndexRecord record = recordsById == null ? null : recordsById.get(hit.getId());
                    if (record == null) {
                        continue;
                    }
                    result = fromRecord(record);
                    result.setFusionScore(0.0d);
                    candidates.put(hit.getId(), result);
                }
                result.setSparseRank(rank);
                result.setTermScore(hit.getScore());
                result.setFusionScore(result.getFusionScore() + sparseWeight / (rrfK + rank));
            }
        }

        List<HybridSearchResult> results = new ArrayList<>(candidates.values());
        results.sort(Comparator
                .comparing(HybridSearchResult::getFusionScore, Comparator.reverseOrder())
                .thenComparing(ReciprocalRankFusion::bestRank)
                .thenComparing(HybridSearchResult::getId));
        if (limit > 0 && results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    private static HybridSearchResult fromRecord(IndexRecord record) {
        return HybridSearchResult.builder()
                .id(record.getId())
                .document(record.getDocument())
                .metadata(record.getMetadata())
                .distance(record.getDistance())
                .fusionScore(0.0d)
                .build();
    }

    private static int bestRank(HybridSearchResult result) {
        int denseRank = result.getDenseRank() == null ? Integer.MAX_VALUE : result.getDenseRank();
        int sparseRank = result.getSparseRank() == null ? Integer.MAX_VALUE : result.getSparseRank();
        return Math.min(denseRank, sparseRank);
    }
}
