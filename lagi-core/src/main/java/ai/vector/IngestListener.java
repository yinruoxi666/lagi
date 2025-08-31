package ai.vector;

import ai.common.pojo.FileChunkResponse;

import java.util.List;

public interface IngestListener {

    // 切片完成（把 docs 的结构性信息/摘要发出去；若要原文可酌情裁剪）
    void onSplitReady(String fileId, List<List<FileChunkResponse.Document>> docs);

    // 某一组（外层 List 的第 idx 组）问答抽取完成
    void onQaGroupReady(String fileId, int groupIndex, List<FileChunkResponse.Document> qaDocs);
    void onQaChunk(String fileId, int groupIndex, int docIndex, List<FileChunkResponse.Document> qaDocs );
    void onComplete(String fileId);
    // 可选：错误
    void onError(String fileId, Throwable t);
}
