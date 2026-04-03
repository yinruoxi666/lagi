package ai.pnps.skill;

/**
 * A tiny abstraction over LLM completion.
 * <p>
 * 实现可接入 {@code CompletionsService}，也可在单元测试里用假实现替换。
 */
@FunctionalInterface
public interface SkillLlmClient {
    /**
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词（会要求返回 JSON）
     */
    String generate(String systemPrompt, String userPrompt);
}

