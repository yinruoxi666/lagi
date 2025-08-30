package ai.vector;

import ai.common.pojo.FileChunkResponse;

import java.util.List;


@FunctionalInterface
public interface QaChunkCallback {
    void onChunk(int groupIndex, int chunkIndex, List<FileChunkResponse.Document> block);
}
