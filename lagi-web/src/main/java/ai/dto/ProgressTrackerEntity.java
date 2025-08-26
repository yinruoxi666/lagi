package ai.dto;

import ai.common.pojo.FileChunkResponse;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ProgressTrackerEntity {
    private String taskId;
    private int progress; // 进度（0-100）
    private final ConcurrentHashMap<String, FileDto> fileMap = new ConcurrentHashMap<>();

    public ProgressTrackerEntity(String taskId) {
        this.taskId = taskId;
        this.progress = 0;
    }

    public void addPlaceholder(String filepath, String filename) {
        FileDto dto = new FileDto();
        dto.setFilename(filename);
        dto.setFilepath(filepath);
        dto.setStatus("PROCESSING");
        fileMap.put(filepath, dto);
    }


    public void markRunning(String filepath) {
        FileDto dto = fileMap.get(filepath);
        if (dto != null) {
            dto.setStatus("RUNNING");
        }
    }

    public void markSuccess(String filepath, String fileId, List<List<String>> vectorIds) {
        FileDto dto = fileMap.get(filepath);
        if (dto != null) {
            dto.setStatus("SUCCESS");
            dto.setFileId(fileId);

        }
    }
    public void markFailed(String filepath, String error) {
        FileDto dto = fileMap.get(filepath);
        if (dto == null) {
            dto = new FileDto();
            dto.setFilepath(filepath);
            fileMap.put(filepath, dto);
        }
        dto.setStatus("FAILED");
    }
    public void saveSplitResult(String fileName, List<List<FileChunkResponse.Document>> docs) {

    }

    public void updateFileStage(String fileName, Integer chunkSize, String msg, String content) {

    }

    public void saveQaGroup(String fileName, Integer index, List<FileChunkResponse.Document> qaDocs){

    }
    public String getTaskId() {
        return taskId;
    }

    public List<FileDto> getFilesSnapshot() {
        return new java.util.ArrayList<>(fileMap.values());
    }
    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }
}
