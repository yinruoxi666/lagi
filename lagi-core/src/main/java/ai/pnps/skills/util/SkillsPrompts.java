package ai.pnps.skills.util;

import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillCatalog;
import ai.pnps.skills.pojo.SkillEntry;
import ai.pnps.skills.pojo.SkillExecutionPlan;

public final class SkillsPrompts {
    private SkillsPrompts() {
    }

    public static String selectionSystemPrompt() {
        return "You are a skill selector.\n" +
                "Decide whether the user query needs a skill.\n" +
                "Output JSON only:\n" +
                "{\n" +
                "  \"needs_skills\": true|false,\n" +
                "  \"chat_response\": \"string or null\",\n" +
                "  \"selected_skill_key\": \"string or null\",\n" +
                "  \"reasoning\": \"short reason\"\n" +
                "}\n" +
                "Rules:\n" +
                "- If no skill is needed, set needs_skills=false and provide chat_response.\n" +
                "- If skill is needed, choose exactly one key from catalog.";
    }

    public static String buildSelectionUserPrompt(String userQuery, SkillCatalog catalog) {
        StringBuilder sb = new StringBuilder();
        sb.append("User query:\n").append(userQuery).append("\n\n");
        sb.append("Available skills catalog:\n").append(catalog.getPrompt()).append("\n");
        return sb.toString();
    }

    public static String planSystemPrompt() {
        return "You are a skill executor planner.\n" +
                "Given one selected SKILL.md and user request, output JSON only:\n" +
                "{\n" +
                "  \"mode\": \"script\" | \"chat\",\n" +
                "  \"chat_response\": \"string or null\",\n" +
                "  \"script\": {\n" +
                "    \"argv\": [\"python\",\"scripts/foo.py\",\"arg1\"],\n" +
                "    \"path\": \"legacy relative script path (python) under skill dir\",\n" +
                "    \"args\": [\"arg1\", \"arg2\"],\n" +
                "    \"working_dir\": \"relative working dir\"\n" +
                "  },\n" +
                "  \"reasoning\": \"short reason\"\n" +
                "}\n" +
                "Rules:\n" +
                "- Use mode=chat if script execution is unnecessary.\n" +
                "- Prefer script.argv (OpenClaw-style: argv[0]=interpreter, rest=args; no shell).\n" +
                "- Or legacy script.path + args (runs as python script path with args).";
    }

    public static String buildPlanUserPrompt(String userQuery, SkillEntry selectedSkill, String skillMarkdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("User query:\n").append(userQuery).append("\n\n");
        sb.append("Selected skill key: ").append(selectedSkill.getKey()).append("\n");
        sb.append("Selected skill name: ").append(selectedSkill.getName()).append("\n\n");
        sb.append("SKILL.md content:\n").append(skillMarkdown).append("\n");
        return sb.toString();
    }

    public static String finalAnswerSystemPrompt() {
        return "You are an assistant that summarizes skill execution results.\n" +
                "Answer in Chinese-simplified. Be concise and practical.";
    }

    public static String toolLoopSystemPrompt() {
        return "You are an OpenClaw-like skill agent.\n" +
                "You MUST output JSON only.\n" +
                "Schema:\n" +
                "{\n" +
                "  \"type\": \"tool_call\" | \"final\",\n" +
                "  \"tool_name\": \"read\" | \"exec\" | null,\n" +
                "  \"arguments\": {\n" +
                "    \"argv\": [\"python\",\"scripts/foo.py\",\"arg\"],\n" +
                "    \"path\": \"legacy relative path under selected skill dir\",\n" +
                "    \"working_dir\": \"relative dir under selected skill dir\",\n" +
                "    \"args\": [\"...\"]\n" +
                "  },\n" +
                "  \"final_response\": \"string or null\",\n" +
                "  \"reasoning\": \"short reason\"\n" +
                "}\n" +
                "Rules:\n" +
                "- First check available_skills and selected skill metadata.\n" +
                "- Progressive disclosure: read SKILL.md with read tool before exec when instructions are needed.\n" +
                "- Never read more than one skill up front.\n" +
                "- Use exec with argv (preferred) or legacy path+args for command execution.\n" +
                "- Use final once enough information is available.";
    }

    public static String buildToolLoopUserPrompt(String userQuery,
                                                 SkillCatalog catalog,
                                                 SkillEntry selectedSkill,
                                                 String toolHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("User query:\n").append(userQuery).append("\n\n");
        sb.append("Skills catalog:\n").append(catalog.getPrompt()).append("\n");
        sb.append("Selected skill:\n");
        sb.append("- key: ").append(selectedSkill.getKey()).append("\n");
        sb.append("- name: ").append(selectedSkill.getName()).append("\n");
        sb.append("- location: ").append(selectedSkill.getSkillMdPath()).append("\n\n");
        sb.append("Available tools:\n");
        sb.append("1) read(path)\n");
        sb.append("2) exec(argv | path+args, working_dir)\n\n");
        sb.append("Tool history:\n").append(toolHistory == null ? "(empty)" : toolHistory).append("\n");
        return sb.toString();
    }

    public static String toolDrivenPlanSystemPrompt() {
        return "You are a tool-driven skill runner.\n" +
                "Given one selected SKILL.md and user request, output JSON only:\n" +
                "{\n" +
                "  \"mode\": \"tool\" | \"chat\",\n" +
                "  \"chat_response\": \"string or null\",\n" +
                "  \"tool\": {\n" +
                "    \"name\": \"exec\" | \"read\",\n" +
                "    \"argv\": [\"node\",\"scripts/bar.js\",\"arg\"],\n" +
                "    \"path\": \"legacy relative path under skill dir\",\n" +
                "    \"args\": [\"arg1\", \"arg2\"],\n" +
                "    \"working_dir\": \"relative working dir\"\n" +
                "  },\n" +
                "  \"reasoning\": \"short reason\"\n" +
                "}\n" +
                "Rules:\n" +
                "- Use mode=chat if no tool call is needed.\n" +
                "- If mode=tool and name=exec, prefer argv; or legacy path as python script under skill dir.\n" +
                "- If mode=tool and name=read, path must be a relative file path under skill dir.";
    }

    public static String buildToolDrivenPlanUserPrompt(String userQuery, SkillEntry selectedSkill, String skillMarkdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("User query:\n").append(userQuery).append("\n\n");
        sb.append("Selected skill key: ").append(selectedSkill.getKey()).append("\n");
        sb.append("Selected skill name: ").append(selectedSkill.getName()).append("\n\n");
        sb.append("SKILL.md content:\n").append(skillMarkdown).append("\n");
        sb.append("Runtime note: use only one tool call in this planning output.\n");
        return sb.toString();
    }

    public static String buildFinalAnswerUserPrompt(String userQuery, SkillEntry skillEntry, SkillExecutionPlan plan, ScriptExecutionResult execResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original user query:\n").append(userQuery).append("\n\n");
        sb.append("Selected skill: ").append(skillEntry.getKey()).append(" / ").append(skillEntry.getName()).append("\n");
        sb.append("Plan mode: ").append(plan.getMode()).append("\n");
        sb.append("Plan reasoning: ").append(plan.getReasoning()).append("\n\n");
        sb.append("Execution result:\n");
        sb.append("exit_code=").append(execResult.getExitCode())
                .append(", timeout=").append(execResult.isTimeout())
                .append(", no_output_timeout=").append(execResult.isNoOutputTimeout()).append("\n");
        sb.append(execResult.getOutput()).append("\n");
        sb.append("Please answer user request based on this result.");
        return sb.toString();
    }
}
