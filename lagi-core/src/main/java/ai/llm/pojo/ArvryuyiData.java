package ai.llm.pojo;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ArvryuyiData {
    private String appmsg; // 命令： clear: 清除记忆
    private Integer error; //错误码
    private String text; // 请求的文本
    private String topic; // 场景主题，构建时指定（可更改）
    private ArvryuyiCurData curdata; //当前节点内容
}
