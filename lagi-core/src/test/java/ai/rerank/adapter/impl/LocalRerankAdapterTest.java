package ai.rerank.adapter.impl;

import ai.rerank.adapter.RerankAdapterResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalRerankAdapterTest {

    @Test
    void mapsDuplicateDocumentsToDistinctOriginalIndices() {
        List<RerankAdapterResult.Result> results = LocalRerankAdapter.mapToOriginalIndices(
                Arrays.asList("same", "other", "same"),
                Arrays.asList("same", "same", "other"));

        assertEquals(Arrays.asList(0, 2, 1), Arrays.asList(
                results.get(0).getIndex(), results.get(1).getIndex(), results.get(2).getIndex()));
    }

    @Test
    void rejectsDocumentsOutsideOriginalInput() {
        assertThrows(IllegalStateException.class, () -> LocalRerankAdapter.mapToOriginalIndices(
                Arrays.asList("one", "two"), Arrays.asList("two", "unknown")));
    }
}
