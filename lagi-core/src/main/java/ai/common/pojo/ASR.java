package ai.common.pojo;

import lombok.Builder;

import java.util.List;

@Builder
public class ASR {
    private List<Backend> backends;

    public List<Backend> getBackends() {
        return backends;
    }

    public void setBackends(List<Backend> backends) {
        this.backends = backends;
    }

    @Override
    public String toString() {
        return "ASR [backends=" + backends + "]";
    }
}