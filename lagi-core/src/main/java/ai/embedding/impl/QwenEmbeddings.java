package ai.embedding.impl;

import ai.embedding.EmbeddingConstant;
import ai.embedding.Embeddings;
import ai.common.pojo.EmbeddingConfig;
import ai.vector.diagnostics.VectorSearchPerformanceContext;
import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.common.cache.Cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QwenEmbeddings implements Embeddings {
    private final String apiKey;
    private static final Cache<List<String>, List<List<Float>>> cache = EmbeddingConstant.getEmbeddingCache();

    public QwenEmbeddings(EmbeddingConfig config) {
        this.apiKey = config.getApi_key();
    }

    @Override
    public List<List<Float>> createEmbedding(List<String> docs) {
        int batchSize = 25;
        List<List<Float>> result = new ArrayList<>();
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<String> batchDocs = docs.subList(i, Math.min(i + batchSize, docs.size()));
            List<List<Float>> batchResult = createEmbeddingBatch(batchDocs);
            result.addAll(batchResult);
        }
        return result;
    }

    public List<List<Float>> createEmbeddingBatch(List<String> docs) {
        long totalStartedNanos = VectorSearchPerformanceContext.startTimer();
        int characterCount = 0;
        for (String doc : docs) {
            characterCount += doc == null ? 0 : doc.length();
        }
        try {
            long cacheStartedNanos = VectorSearchPerformanceContext.startTimer();
            List<List<Float>> result = cache.getIfPresent(docs);
            VectorSearchPerformanceContext.recordCache("embedding", result != null,
                    VectorSearchPerformanceContext.elapsedMillis(cacheStartedNanos));
            if (result != null) {
                int dimension = result.isEmpty() || result.get(0) == null ? 0 : result.get(0).size();
                VectorSearchPerformanceContext.info(
                        "Qwen embedding缓存返回：文档数={}，字符数={}，向量数={}，维度={}",
                        docs.size(), characterCount, result.size(), dimension);
                return result;
            }

            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .apiKey(this.apiKey)
                    .model(TextEmbedding.Models.TEXT_EMBEDDING_V2)
                    .texts(docs).build();
            TextEmbedding textEmbedding = new TextEmbedding();
            TextEmbeddingResult textEmbeddingResult;
            long remoteStartedNanos = VectorSearchPerformanceContext.startTimer();
            VectorSearchPerformanceContext.info(
                    "Qwen embedding外部调用开始：model={}，文档数={}，字符数={}",
                    TextEmbedding.Models.TEXT_EMBEDDING_V2, docs.size(), characterCount);
            try {
                textEmbeddingResult = textEmbedding.call(param);
            } catch (NoApiKeyException e) {
                VectorSearchPerformanceContext.error("Qwen embedding外部调用异常：缺少API密钥", e);
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                VectorSearchPerformanceContext.error("Qwen embedding外部调用异常", e);
                throw e;
            } finally {
                VectorSearchPerformanceContext.recordStage("embedding.remote", "Qwen embedding外部调用",
                        VectorSearchPerformanceContext.elapsedMillis(remoteStartedNanos),
                        VectorSearchPerformanceContext.EMBEDDING_WARN_MS);
            }

            long conversionStartedNanos = VectorSearchPerformanceContext.startTimer();
            result = new ArrayList<>();
            for (TextEmbeddingResultItem item : textEmbeddingResult.getOutput().getEmbeddings()) {
                List<Float> embedding = new ArrayList<>();
                for (Double value : item.getEmbedding()) {
                    embedding.add(value.floatValue());
                }
                result.add(embedding);
            }
            VectorSearchPerformanceContext.recordStage("embedding.convert", "Qwen embedding结果转换",
                    VectorSearchPerformanceContext.elapsedMillis(conversionStartedNanos),
                    VectorSearchPerformanceContext.HTTP_WARN_MS);
            if (!result.isEmpty()) {
                long cachePutStartedNanos = VectorSearchPerformanceContext.startTimer();
                cache.put(docs, result);
                VectorSearchPerformanceContext.recordStage("embedding.cachePut", "Qwen embedding缓存写入",
                        VectorSearchPerformanceContext.elapsedMillis(cachePutStartedNanos),
                        VectorSearchPerformanceContext.HTTP_WARN_MS);
            }
            int dimension = result.isEmpty() || result.get(0) == null ? 0 : result.get(0).size();
            VectorSearchPerformanceContext.info(
                    "Qwen embedding处理完成：文档数={}，字符数={}，向量数={}，维度={}",
                    docs.size(), characterCount, result.size(), dimension);
            return result;
        } finally {
            VectorSearchPerformanceContext.recordStage("embedding.total", "Qwen embedding总阶段",
                    VectorSearchPerformanceContext.elapsedMillis(totalStartedNanos),
                    VectorSearchPerformanceContext.EMBEDDING_WARN_MS);
        }
    }

    @Override
    public List<Float> createEmbedding(String doc) {
        List<String> docs = Collections.singletonList(doc);
        List<List<Float>> result = createEmbedding(docs);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
}
