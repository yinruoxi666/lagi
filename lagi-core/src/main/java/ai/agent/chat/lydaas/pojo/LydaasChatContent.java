package ai.agent.chat.lydaas.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LydaasChatContent {
    private String docAskContent;
    private String text;
    private String contentType;
    private String audioFile;
}
