package ai.vector.retrieval;

import ai.vector.pojo.IndexRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QaPairTextAssemblerTest {

    @Test
    void assemblesAnswerWhenQuestionIsRetrieved() {
        IndexRecord answer = record("a-1", "行李箱限额需要咨询柜台", "q-1", "qa");

        String text = QaPairTextAssembler.assemble(
                "q-1", "机场行李限额", metadata("", "qa"), "demo",
                lookup(null, Collections.singletonList(answer)));

        assertEquals("机场行李限额$$$行李箱限额需要咨询柜台", text);
    }

    @Test
    void assemblesQuestionWhenAnswerIsRetrieved() {
        IndexRecord question = record("q-1", "机场行李限额", "", "qa");

        String text = QaPairTextAssembler.assemble(
                "a-1", "行李箱限额需要咨询柜台", metadata("q-1", "qa"), "demo",
                lookup(question, Collections.emptyList()));

        assertEquals("机场行李限额$$$行李箱限额需要咨询柜台", text);
    }

    @Test
    void ignoresNonQaRecords() {
        String text = QaPairTextAssembler.assemble(
                "doc-1", "普通文档", metadata("", "file"), "demo",
                lookup(null, Collections.emptyList()));

        assertEquals("普通文档", text);
    }

    @Test
    void keepsOriginalQuestionWhenMatchingAnswerIsMissing() {
        IndexRecord unrelated = record("a-2", "其他答案", "q-2", "qa");

        String text = QaPairTextAssembler.assemble(
                "q-1", "机场行李限额", metadata("", "qa"), "demo",
                lookup(null, Arrays.asList(unrelated, null)));

        assertEquals("机场行李限额", text);
    }

    private static QaPairTextAssembler.PairLookup lookup(IndexRecord byId,
                                                          List<IndexRecord> byParentId) {
        return new QaPairTextAssembler.PairLookup() {
            @Override
            public IndexRecord findById(String id, String category) {
                return byId;
            }

            @Override
            public List<IndexRecord> findByParentId(String parentId, String category) {
                return byParentId;
            }
        };
    }

    private static IndexRecord record(String id, String document, String parentId, String source) {
        return IndexRecord.builder()
                .id(id)
                .document(document)
                .metadata(metadata(parentId, source))
                .build();
    }

    private static Map<String, Object> metadata(String parentId, String source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parent_id", parentId);
        metadata.put("source", source);
        return metadata;
    }
}
