package ai.servlet.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VectorQueryRequest {
    private String text;
    private Integer n;
    private Map<String, String> where = new HashMap<>();
    private String category;

    private List<String> ids;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public Map<String, String> getWhere() {
        return where;
    }

    public void setWhere(Map<String, String> where) {
        this.where = where;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getIds() {
        return ids;
    }
    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    @Override
    public String toString() {
        return "VectorQueryRequest{" + "text='" + text + '\'' + "," +
                " n=" + n + ", where=" + where + ", category='" + category + '\'' + '}';
    }
}
