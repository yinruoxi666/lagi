package ai.dto;

import ai.common.pojo.FileChunkResponse;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProgressTrackerEntity {
    private String taskId;
    private int progress; // 进度（0-100）
    private final ConcurrentHashMap<String, FileDto> fileMap = new ConcurrentHashMap<>();

    public ProgressTrackerEntity(String taskId) {
        this.taskId = taskId;
        this.progress = 0;
    }

    public void addPlaceholder(String fileId, String filename,String filepath) {
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
            if (vectorIds != null && vectorIds.size() > 0) {
                List<String> vectorIdList = vectorIds.get(0);
                // 更新每个文档的 vectorId
                List<DocDto> docs = dto.getDocs();
                if (docs != null) {
                    for (int i = 0; i < docs.size() && i < vectorIdList.size(); i++) {
                        docs.get(i).setVectorId(vectorIdList.get(i));
                    }
                }
            }
            dto.setStatus("SUCCESS");
            dto.setFileId(fileId);
            setProgress(100);
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

    public void updateFileInfo(String filepath, String fileId, String filename) {
        FileDto dto = fileMap.get(filepath);
        if (dto == null) {
            dto = new FileDto();
            dto.setFilepath(filepath);
            fileMap.put(filepath, dto);
        }
        dto.setFileId(fileId);
        dto.setFilename(filename);
        dto.setFilepath(filepath);
        dto.setStatus("UPLOADED");
        fileMap.put(filepath, dto);
    }
    public void saveSplitResult(String fileName, List<List<FileChunkResponse.Document>> docs) {
        FileDto dto = fileMap.get(fileName);
        List<FileChunkResponse.Document> processedDocs = docs.get(0);
        dto.setDocs(processedDocs.stream().map(item-> {
            DocDto docDto = new DocDto();
            docDto.setContent(item.getText());
            docDto.setStatus("PROCESSED");
            return docDto;
        }).collect(Collectors.toList()));
    }

    public void updateFileStage(String fileName, Integer chunkSize, String msg, String content) {
        FileDto dto = fileMap.get(fileName);
        if (dto != null) {
            dto.setStatus("msg");
        }
    }

    public void saveQaGroup(String fileName, Integer index, List<FileChunkResponse.Document> qaDocs){
        System.out.println("saveQaGroup: " + fileName + ", index: " + index + ", qaDocs size: " + (qaDocs == null ? 0 : qaDocs.size()));
        for (FileChunkResponse.Document doc : qaDocs) {
            System.out.println("  doc content: " + doc.getText());
        }
    }

    public void saveQaChunk(String fileName, Integer groupIndex, Integer docIndex, List<FileChunkResponse.Document> qaDocs){
        System.out.println("saveQaGroup: " + fileName + ", index: " + docIndex + ", qaDocs size: " + (qaDocs == null ? 0 : qaDocs.size()));
        FileDto dto = fileMap.get(fileName);
        dto.getDocs().get(docIndex).setQuestions(qaDocs.stream().map(FileChunkResponse.Document::getText).collect(Collectors.toList()));
        for (FileChunkResponse.Document doc : qaDocs) {
            System.out.println("  doc content: " + doc.getText());
        }
        dto.getDocs().get(docIndex).setStatus("COMPLETE");
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
