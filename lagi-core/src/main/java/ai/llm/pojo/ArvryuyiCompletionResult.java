package ai.llm.pojo;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ArvryuyiCompletionResult {

    private String function;
    private String packType;
    private String source;
    private Integer code; // 0: 无错误
    private String msg; //错误说明
    private ArvryuyiData data;
}
