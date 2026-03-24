package ai.openai.pojo;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Usage  implements Serializable {
    private long prompt_tokens;
    private long completion_tokens;
    private long total_tokens;
    private PromptTokensDetails prompt_tokens_details;
    private CompletionTokensDetails completion_tokens_details;
    private long prompt_cache_hit_tokens;
    private long prompt_cache_miss_tokens;
    /** Estimated saved tokens (filled by hooks using a random ratio of {@code total_tokens}). */
    private long saved_tokens;
}
