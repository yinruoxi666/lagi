package ai.common.pojo;

import java.util.List;

public class LLM {
    private EmbeddingConfig embedding;
    private List<Backend> backends;
    private String streamBackend;

    public EmbeddingConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingConfig embedding) {
        this.embedding = embedding;
    }

    public List<Backend> getBackends() {
        return backends;
    }

    public void setBackends(List<Backend> backends) {
        this.backends = backends;
    }

    public String getStreamBackend() {
        return streamBackend;
    }

    public void setStreamBackend(String streamBackend) {
        this.streamBackend = streamBackend;
    }
}