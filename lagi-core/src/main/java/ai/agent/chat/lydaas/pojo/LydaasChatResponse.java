package ai.agent.chat.lydaas.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LydaasChatResponse {
    private String msgId;
    private String msgType;
    private Long totalTokens;
    private Long spendTime;
    private String chatId;
    private String chatStatus;
    private String conversationId;
    private Boolean isEnd;
    private Boolean firstMessageFlag;
    private LydaasChatContent content;
    private Boolean messageEnd;
    private Boolean hasAnswer;
    private String contentType;
    private LydaasThoughtChainContentInfo thoughtChainContentInfo;
}
