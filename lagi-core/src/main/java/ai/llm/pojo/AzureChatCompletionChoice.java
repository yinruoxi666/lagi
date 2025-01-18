package ai.llm.pojo;

import ai.openai.pojo.ChatCompletionChoice;
import lombok.*;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AzureChatCompletionChoice extends ChatCompletionChoice {

    private Map<String, AzureFilter> content_filter_results;
}
