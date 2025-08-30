package ai.dto;

import java.util.List;

public class FileDto {
    private String fileId;
    private String filename;
    private String filepath;
    private String status;
    List<DocDto> docs;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public List<DocDto> getDocs() {
        return docs;
    }

    public void setDocs(List<DocDto> docs) {
        this.docs = docs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
