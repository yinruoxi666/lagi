package ai.common.pojo;

import java.util.List;

public class ImageGeneration {
    private List<Backend> backends;

    public List<Backend> getBackends() {
        return backends;
    }

    public void setBackends(List<Backend> backends) {
        this.backends = backends;
    }

    @Override
    public String toString() {
        return "ImageGeneration [backends=" + backends + "]";
    }
}