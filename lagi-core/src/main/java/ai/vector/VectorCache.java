package ai.vector;

import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.utils.LRUCache;

public class VectorCache {
    private static final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();

    private static final VectorCache INSTANCE = new VectorCache();
    private static final LRUCache<String, IndexSearchData> vectorLinkCache;
    private static final LRUCache<String, IndexSearchData> parentElementCache;
    private static final LRUCache<String, IndexSearchData> childElementCache;
    private static int CACHE_SIZE;

    static {
        CACHE_SIZE = VectorStoreConstant.VECTOR_CACHE_SIZE;
        if (RAG_CONFIG.getCacheSize() != null) {
            CACHE_SIZE = RAG_CONFIG.getCacheSize();
        }
        vectorLinkCache = new LRUCache<>(CACHE_SIZE);
        parentElementCache = new LRUCache<>(CACHE_SIZE);
        childElementCache = new LRUCache<>(CACHE_SIZE);
    }

    private VectorCache() {
    }

    public static VectorCache getInstance() {
        return INSTANCE;
    }

    public IndexSearchData getFromVectorLinkCache(String id) {
        return vectorLinkCache.get(id);
    }

    public void putToVectorLinkCache(String id, IndexSearchData extendedIndexSearchData) {
        vectorLinkCache.put(id, extendedIndexSearchData);
    }

    public IndexSearchData getFromParentElementCache(String id) {
        return parentElementCache.get(id);
    }

    public boolean isParentCacheFull() {
        return parentElementCache.size() >= CACHE_SIZE;
    }

    public void putToParentElementCache(String id, IndexSearchData extendedIndexSearchData) {
        parentElementCache.put(id, extendedIndexSearchData);
    }

    public IndexSearchData getFromChildElementCache(String id) {
        return childElementCache.get(id);
    }

    public void putToChildElementCache(String id, IndexSearchData extendedIndexSearchData) {
        childElementCache.put(id, extendedIndexSearchData);
    }

    public boolean isChildCacheFull() {
        return childElementCache.size() >= CACHE_SIZE;
    }
}
