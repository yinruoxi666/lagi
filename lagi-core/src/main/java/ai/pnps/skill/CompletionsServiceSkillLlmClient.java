package ai.pnps.skill;

import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatCompletionChoice;

import java.util.Objects;

/**
 * Bridge {@link SkillLlmClient} to the existing {@link CompletionsService}.
 */
public class CompletionsServiceSkillLlmClient implements SkillLlmClient {

    private final CompletionsService completionsService;
    private final String category;

    public CompletionsServiceSkillLlmClient(CompletionsService completionsService, String category) {
        this.completionsService = Objects.requireNonNull(completionsService, "completionsService");
        this.category = category;
    }

    public CompletionsServiceSkillLlmClient() {
        this(new CompletionsService(), "skill-agent");
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        ChatCompletionRequest req = completionsService.getCompletionsRequest(systemPrompt, userPrompt, category);
        ChatCompletionResult res = completionsService.completions(req);
        if (res == null || res.getChoices() == null || res.getChoices().isEmpty()) {
            return "";
        }
        ChatCompletionChoice choice = res.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            return "";
        }
        return choice.getMessage().getContent();
    }
}

