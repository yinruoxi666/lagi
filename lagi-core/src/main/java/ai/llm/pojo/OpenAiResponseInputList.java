package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiResponseInputList implements OpenAiResponseInput {

    private List<OpenAiResponseInputItem> items;

}
