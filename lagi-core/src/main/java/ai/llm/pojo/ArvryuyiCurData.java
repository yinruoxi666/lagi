package ai.llm.pojo;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ArvryuyiCurData {
    private String action;
    private String answer;// 节点回答
    private String nodeName; // 节点名称
    private String leadword; // 节点引导语
    private String askback; // 节点反馈字段
    private String ricktext; // 图片链接
}
