package ai.agent.chat.lydaas.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LydaasChatDataItem {
    private String conversationId;
    private String chatId;
}
