package ai.utils;

import org.apache.hadoop.thirdparty.com.google.common.collect.Lists;

import java.util.List;


public class EmbeddingSimilarityCalculator {


    public static double calculateCosineSimilarity(List<Float> embedding1, List<Float> embedding2) {
        if (embedding1 == null || embedding2 == null) {
            throw new IllegalArgumentException("Embedding List不能为null");
        }
        if (embedding1.isEmpty() || embedding2.isEmpty()) {
            throw new IllegalArgumentException("Embedding List不能为空（长度为0）");
        }
        if (embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("两个Embedding List长度必须一致！embedding1长度："
                    + embedding1.size() + "，embedding2长度：" + embedding2.size());
        }

        double dotProduct = 0.0;
        double norm1Squared = 0.0;
        double norm2Squared = 0.0;

        int size = embedding1.size();
        for (int i = 0; i < size; i++) {
            Float f1 = embedding1.get(i);
            Float f2 = embedding2.get(i);
            if (f1 == null || f2 == null) {
                throw new IllegalArgumentException("Embedding List中包含null元素（索引：" + i + "）");
            }
            double val1 = f1.doubleValue();
            double val2 = f2.doubleValue();

            dotProduct += val1 * val2;
            norm1Squared += val1 * val1;
            norm2Squared += val2 * val2;
        }

        double norm1 = Math.sqrt(norm1Squared);
        double norm2 = Math.sqrt(norm2Squared);
        if (norm1 == 0 || norm2 == 0) {
            throw new IllegalArgumentException("Embedding向量模长为0（所有元素均为0）");
        }

        double cosine = dotProduct / (norm1 * norm2);
        cosine = Math.max(Math.min(cosine, 1.0), -1.0);
        return (cosine + 1.0) / 2.0;
    }


    public static double calculateEuclideanDistance(List<Float> embedding1, List<Float> embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("Embedding List输入不合法");
        }

        double sum = 0.0;
        int size = embedding1.size();
        for (int i = 0; i < size; i++) {
            Float f1 = embedding1.get(i);
            Float f2 = embedding2.get(i);
            if (f1 == null || f2 == null) {
                throw new IllegalArgumentException("Embedding List包含null元素（索引：" + i + "）");
            }
            double diff = f1.doubleValue() - f2.doubleValue();
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

}