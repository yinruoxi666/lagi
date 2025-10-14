package ai.vector;

import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.utils.LRUCache;
import cn.hutool.core.bean.BeanUtil;

import java.util.List;

public class VectorCache {
    private static final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();

    private static final VectorCache INSTANCE = new VectorCache();
    private static final LRUCache<String, List<IndexSearchData>> vectorLinkCache;
    private static final LRUCache<String, IndexSearchData> parentElementCache;
    private static final LRUCache<String, List<IndexSearchData>> childElementCache;
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

    public List<IndexSearchData> getFromVectorLinkCache(String id) {
        return vectorLinkCache.get(id);
    }

    public void putToVectorLinkCache(String id, List<IndexSearchData> extendedIndexSearchData) {
        vectorLinkCache.put(id, extendedIndexSearchData);
    }

    public void removeFromVectorLinkCache(String id) {
        vectorLinkCache.remove(id);
    }

    public IndexSearchData getFromParentElementCache(String id) {
        return parentElementCache.get(id);
    }

    public boolean isParentCacheFull() {
        return parentElementCache.size() >= CACHE_SIZE;
    }

    public void putToParentElementCache(String id, IndexSearchData extendedIndexSearchData) {
        // 2025-09-22 防止引用对象被外部修改，导致缓存数据不一致，改为拷贝对象存储
//        IndexSearchData copyObj = new IndexSearchData();
//        BeanUtil.copyProperties(extendedIndexSearchData, copyObj);
//        System.out.println("Put to parent cache,id=" + id + ",copyObj id= " + copyObj.getId());
        parentElementCache.put(id, extendedIndexSearchData);
    }

    public void removeFromParentElementCache(String id) {
        parentElementCache.remove(id);
    }

    public List<IndexSearchData> getFromChildElementCache(String id) {
        return childElementCache.get(id);
    }

    public void putToChildElementCache(String id, List<IndexSearchData> extendedIndexSearchData) {
        childElementCache.put(id, extendedIndexSearchData);
    }

    public void removeFromChildElementCache(String id) {
        childElementCache.remove(id);
    }

    public boolean isChildCacheFull() {
        return childElementCache.size() >= CACHE_SIZE;
    }

    public void removeFromAllCache(String id) {
        removeFromVectorLinkCache(id);
        removeFromParentElementCache(id);
        removeFromChildElementCache(id);
    }
}
