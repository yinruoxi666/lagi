package ai.pnps.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为检索/LLM 选择构建语料行（对齐 ms-agent {@code AutoSkills._build_corpus}）。
 */
public final class SkillCorpus {

    private SkillCorpus() {
    }

    public static String corpusLine(String skillKey, SkillSchema skill) {
        return "[" + skillKey + "] " + skill.getName() + ": " + skill.getDescription();
    }

    /**
     * @return 语料文档列表，及「文档 → skillKey」映射（文档字符串与列表元素一致，便于反查）
     */
    public static CorpusIndex buildIndex(Map<String, SkillSchema> skills) {
        List<String> corpus = new ArrayList<>();
        Map<String, String> docToSkillKey = new LinkedHashMap<>();
        for (Map.Entry<String, SkillSchema> e : skills.entrySet()) {
            String key = e.getKey();
            String doc = corpusLine(key, e.getValue());
            corpus.add(doc);
            docToSkillKey.put(doc, key);
        }
        return new CorpusIndex(corpus, docToSkillKey);
    }

    /** 从检索返回的文档行解析 skillKey：优先查表，否则解析前缀 {@code [id@ver]} */
    public static String extractSkillKey(String doc, Map<String, String> docToSkillKey) {
        if (doc == null) {
            return null;
        }
        if (docToSkillKey != null && docToSkillKey.containsKey(doc)) {
            return docToSkillKey.get(doc);
        }
        if (doc.startsWith("[") && doc.contains("]")) {
            int end = doc.indexOf(']');
            if (end > 1) {
                return doc.substring(1, end);
            }
        }
        return null;
    }

    public static final class CorpusIndex {
        private final List<String> corpus;
        private final Map<String, String> docToSkillKey;

        public CorpusIndex(List<String> corpus, Map<String, String> docToSkillKey) {
            this.corpus = corpus;
            this.docToSkillKey = docToSkillKey;
        }

        public List<String> getCorpus() {
            return corpus;
        }

        public Map<String, String> getDocToSkillKey() {
            return docToSkillKey;
        }
    }
}
