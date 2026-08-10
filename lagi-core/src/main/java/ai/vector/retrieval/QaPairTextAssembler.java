package ai.vector.retrieval;

import ai.vector.pojo.IndexRecord;
import cn.hutool.core.util.StrUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ai.vector.VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_QA;

/** Resolves a QA vector record into the stable {@code question$$$answer} text form. */
public final class QaPairTextAssembler {
    private static final String PARENT_ID = "parent_id";
    private static final String SOURCE = "source";
    private static final String DELIMITER = "$$$";

    private QaPairTextAssembler() {
    }

    public static String assemble(String id, String text, Map<String, Object> metadata,
                                  String category, PairLookup lookup) {
        if (!isQa(metadata) || StrUtil.isBlank(id) || lookup == null) {
            return text;
        }

        String parentId = metadataValue(metadata, PARENT_ID);
        if (StrUtil.isNotBlank(parentId)) {
            IndexRecord question = lookup.findById(parentId, category);
            if (isQa(question) && StrUtil.isNotBlank(question.getDocument())
                    && StrUtil.isNotBlank(text)) {
                return question.getDocument() + DELIMITER + text;
            }
            return text;
        }

        List<IndexRecord> answers = lookup.findByParentId(id, category);
        if (answers == null) {
            answers = Collections.emptyList();
        }
        for (IndexRecord answer : answers) {
            if (answer == null || !isQa(answer) || id.equals(answer.getId())
                    || !id.equals(metadataValue(answer.getMetadata(), PARENT_ID))
                    || StrUtil.isBlank(answer.getDocument()) || StrUtil.isBlank(text)) {
                continue;
            }
            return text + DELIMITER + answer.getDocument();
        }
        return text;
    }

    private static boolean isQa(IndexRecord record) {
        return record != null && isQa(record.getMetadata());
    }

    private static boolean isQa(Map<String, Object> metadata) {
        return FILE_CHUNK_SOURCE_QA.equals(metadataValue(metadata, SOURCE));
    }

    private static String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return null;
        }
        return metadata.get(key).toString();
    }

    public interface PairLookup {
        IndexRecord findById(String id, String category);

        List<IndexRecord> findByParentId(String parentId, String category);
    }
}
