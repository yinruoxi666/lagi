package ai.vector;

import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.medusa.utils.PromptCacheConfig;
import ai.utils.LagiGlobal;
import ai.vector.pojo.IndexRecord;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class VectorCacheLoader {

    private static final Logger logger = LoggerFactory.getLogger(VectorCacheLoader.class);
    private static final VectorCache vectorCache = VectorCache.getInstance();
    private static final VectorStoreService vectorStoreService = new VectorStoreService();
    private static final DataStore cacheL2 = new DataStore();
    private static final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();

    @lombok.Data
    @ToString
    public static class Data implements Comparable<Data> {
        private String q;
        private String a;
        private Long seq;

        public Data(String q, String a, Long seq) {
            this.q = q;
            this.a = a;
            this.seq = seq;
        }


        @Override
        public int compareTo(Data other) {
            return Long.compare(this.seq, other.seq);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Data data = (Data) o;
            return Objects.equals(q, data.q);
        }

        @Override
        public int hashCode() {
            return Objects.hash(q);
        }
    }

    public static class DataStore {
        private TreeMap<String, Data> seqToData = new TreeMap<>(((o1, o2) -> {
            Long l1 = Long.valueOf(o1.split(":")[0]);
            Long l2 = Long.valueOf(o2.split(":")[0]);
            if (!l1.equals(l2)) {
                return Long.compare(l1, l2);
            }
            return o1.compareTo(o2);
        }));
        private Map<String, Long> qToSeq = new HashMap<>();

        private String getKey(Long seq, String q) {
            return seq + ":" + q;
        }

        public void add(Data data) {
            qToSeq.put(data.getQ(), data.getSeq());
            seqToData.put(getKey(data.getSeq(), data.getQ()), data);
        }

        public String get(String q) {
            Long seq = qToSeq.get(q);
            if (seq != null) {
                Data data = seqToData.get(getKey(seq, q));
                return data != null ? data.getA() : null;
            }
            return null;
        }

        public Data getDate(String q) {
            Long seq = qToSeq.get(q);
            if (seq == null) {
                return null;
            }
            return seqToData.get(getKey(seq, q));
        }


        public List<String> getNeighbors(String q, int count) {
            List<String> res = new ArrayList<>();
            Data data = getDate(q);
            String source = getKey(data.getSeq(), data.getQ());
            for (int i = 0; i < count; i++) {
                source = seqToData.lowerKey(source);
                if (source == null) {
                    break;
                }
                Data cData = seqToData.get(source);
                if (cData == null) {
                    break;
                }
                res.add(cData.getQ());
            }
            source = getKey(data.getSeq(), data.getQ());
            for (int i = 0; i < count; i++) {
                source = seqToData.higherKey(source);
                if (source == null) {
                    break;
                }
                Data cData = seqToData.get(source);
                if (cData == null) {
                    break;
                }
                res.add(cData.getQ());
            }
            return res;
        }
    }

    public static void load() {
        new Thread(() -> {
            try {
                logger.info("VectorCacheLoader started");
                if (RAG_CONFIG.getPreloadCache() !=null && RAG_CONFIG.getPreloadCache()) {
                    loadParentChildCache();
                }
//                loadVectorLinkCache();
                if (PromptCacheConfig.MEDUSA_ENABLE && LagiGlobal.RAG_ENABLE) {
                    loadMedusaCache();
                }
                logger.info("VectorCacheLoader initialized");
            } catch (Exception e) {
                logger.error("VectorCacheLoader init error", e);
            }
        }).start();
    }

    private static void loadParentChildCache() {
        logger.info("VectorCacheLoader parent and child preload cache loading");
        int offset = 0;
        int limit = 500;
        String allCategory = RAG_CONFIG.getPreloadCacheCategory();
        if (allCategory == null) {
            allCategory = vectorStoreService.getVectorStoreConfig().getDefaultCategory();
        }
        String[] categories = allCategory.split(",");
        for (int i = 0; i < categories.length; i++) {
            String category = categories[i].trim();
            while (true) {
                List<IndexRecord> indexRecordList = vectorStoreService.fetch(limit, offset, category);
                if (indexRecordList.isEmpty() || (
                        vectorCache.isParentCacheFull() &&
                                vectorCache.isChildCacheFull())) {
                    break;
                }
                for (IndexRecord indexRecord : indexRecordList) {
                    String id = indexRecord.getId();
                    String parentId = (String) indexRecord.getMetadata().get("parent_id");
                    vectorStoreService.getParentIndex(parentId, category);
                    vectorStoreService.getChildIndex(id, category);
                }
                offset += limit;
            }
            if (vectorCache.isParentCacheFull() &&
                    vectorCache.isChildCacheFull()) {
                break;
            }
            offset = 0;
        }
        logger.info("VectorCacheLoader parent and child preload cache loaded");
    }

    private static void loadVectorLinkCache() {
        Map<String, String> where = new HashMap<>();
        where.put("filename", "");
        List<IndexRecord> indexRecordList = vectorStoreService.fetch(where);
        for (IndexRecord indexRecord : indexRecordList) {
            IndexSearchData indexSearchData = vectorStoreService.toIndexSearchData(indexRecord);
            IndexSearchData extendedIndexSearchData = vectorStoreService.extendText(indexSearchData);
            vectorCache.putToVectorLinkCache(indexSearchData.getId(), extendedIndexSearchData);
        }
    }

    private static void loadMedusaCache() {
        Map<String, String> where = new HashMap<>();
        where.put("filename", "");
        List<IndexRecord> indexRecordList = vectorStoreService.fetch(where);
        for (IndexRecord indexRecord : indexRecordList) {
            IndexSearchData indexSearchData = vectorStoreService.toIndexSearchData(indexRecord);
            if (indexSearchData.getParentId() != null) {
                IndexSearchData questionIndexData = vectorStoreService.getParentIndex(indexSearchData.getParentId());
                String text = questionIndexData.getText().replaceAll("\n", "");
                Long seq = questionIndexData.getSeq() == null ? 0 : questionIndexData.getSeq();
                put2L2(text, seq, indexSearchData.getText());
            }
        }
    }

    public static void put2L2(String key, Long seq, String value) {
        try {
            cacheL2.add(new Data(key, value, seq));
        } catch (Exception ignored) {
        }
    }

    public static String get2L2(String key) {
        return cacheL2.get(key);
    }


    public static List<String> getFromL2Near(String question, int nearNum) {
        return cacheL2.getNeighbors(question, nearNum);
    }

}
