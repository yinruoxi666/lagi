package ai.llm.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;

import java.util.HashMap;
import java.util.Map;

public class DoubaoTranslationInputContentItem extends InputContentItemText {
    @JsonProperty("translation_options")
    private Map<String, String> options;

    public DoubaoTranslationInputContentItem() {
        options = new HashMap<>();
        options.put("source_language","zh");
        options.put("target_language","en");
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public static DoubaoTranslationInputContentItem.Builder translationBuilder() {
        return new DoubaoTranslationInputContentItem.Builder();
    }

    public static class Builder {
        private String text;
        private Map<String, String> options;

        public DoubaoTranslationInputContentItem.Builder text(String text) {
            this.text = text;
            return this;
        }
        public DoubaoTranslationInputContentItem.Builder transFromTo(String from, String to) {
            this.options = new HashMap<>();
            this.options.put("source_language",from);
            this.options.put("target_language",to);
            return this;
        }

        public DoubaoTranslationInputContentItem build() {
            DoubaoTranslationInputContentItem responsesContentItemText = new DoubaoTranslationInputContentItem();
            responsesContentItemText.setText(this.text);
            responsesContentItemText.setOptions(this.options);
            return responsesContentItemText;
        }
    }
}
