package ai.rerank.service;

import ai.rerank.pojo.RerankResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RerankServiceTest {

    @Test
    void mapsRerankedDuplicateDocumentsBackToDistinctOriginalIndices() {
        List<RerankResponse.RerankResult> results = RerankService.buildResults(
                Arrays.asList("same", "other", "same"),
                Arrays.asList("same", "same", "other"));

        assertEquals(Arrays.asList(0, 2, 1), Arrays.asList(
                results.get(0).getIndex(), results.get(1).getIndex(), results.get(2).getIndex()));
    }

    @Test
    void rejectsDocumentsNotPresentInOriginalInput() {
        assertThrows(IllegalStateException.class, () -> RerankService.buildResults(
                Arrays.asList("one", "two"), Arrays.asList("two", "unknown")));
    }
}
