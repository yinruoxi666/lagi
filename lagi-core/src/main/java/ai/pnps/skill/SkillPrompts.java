package ai.pnps.skill;

/**
 * Prompts ported from ms-agent {@code ms_agent.skill.prompts}.
 */
public final class SkillPrompts {

    private SkillPrompts() {
    }

    /**
     * Direct select skills + DAG in one LLM call.
     * <p>
     * 注意：此 prompt 要求返回 JSON，并包含 {@code selected_skill_ids/dag/execution_order}。
     */
    public static final String PROMPT_DIRECT_SELECT_SKILLS =
            "You are a skill selector. Given a user query and all available skills, select the relevant skills and build an execution DAG.\n" +
            "\n" +
            "User Query: {query}\n" +
            "\n" +
            "All Available Skills (USE THESE EXACT IDs):\n" +
            "{all_skills}\n" +
            "\n" +
            "Tasks:\n" +
            "1. Determine if this query needs skills or is just casual chat\n" +
            "2. If skills are needed, select relevant skills using their EXACT IDs from the list above\n" +
            "3. Build a dependency DAG for the selected skills\n" +
            "\n" +
            "Output in JSON format:\n" +
            "{\n" +
            "    \"needs_skills\": true/false,\n" +
            "    \"chat_response\": \"Direct response if no skills needed, null otherwise\",\n" +
            "    \"selected_skill_ids\": [\"exact_skill_id_from_list\", ...],\n" +
            "    \"dag\": {\n" +
            "        \"exact_skill_id_1\": [\"depends_on_skill_id\"],\n" +
            "        \"exact_skill_id_2\": [],\n" +
            "        ...\n" +
            "    },\n" +
            "    \"execution_order\": [\"first_skill_id\", \"second_skill_id\", ...],\n" +
            "    \"reasoning\": \"Brief explanation of skill selection and dependencies\"\n" +
            "}\n" +
            "\n" +
            "**CRITICAL:**\n" +
            "- ONLY use exact skill IDs from the Available Skills list (e.g., `pdf@latest`, `pptx@latest`, NOT invented names)\n" +
            "- Set `needs_skills` to false if the query is casual chat or can be answered directly\n" +
            "- `execution_order` MUST contain ALL skills from `selected_skill_ids`, ordered by dependencies\n" +
            "- In `dag`, each skill maps to its dependencies (skills it depends on), empty list `[]` means no dependencies\n";

    /**
     * Analyze query to decide if skills are needed, and produce search queries.
     * Ported from ms-agent {@code PROMPT_ANALYZE_QUERY_FOR_SKILLS}.
     */
    public static final String PROMPT_ANALYZE_QUERY_FOR_SKILLS =
            "You are a skill analyzer. Given a user query, identify what types of skills/capabilities are needed, or just chatting is sufficient.\n" +
            "\n" +
            "User Query: {query}\n" +
            "\n" +
            "Available Skills Overview:\n" +
            "{skills_overview}\n" +
            "\n" +
            "Analyze the query and determine:\n" +
            "1. Whether this query requires specific skills/capabilities to fulfill\n" +
            "2. If skills are needed, what capabilities/functions are directly required\n" +
            "3. What prerequisites or dependencies might be required\n" +
            "\n" +
            "Output in JSON format:\n" +
            "{\n" +
            "    \"needs_skills\": true/false,\n" +
            "    \"intent_summary\": \"Brief description of user intent\",\n" +
            "    \"skill_queries\": [\"query1\", \"query2\", ...],\n" +
            "    \"chat_response\": \"Direct response if no skills needed, null otherwise\",\n" +
            "    \"reasoning\": \"Brief explanation\"\n" +
            "}\n" +
            "\n" +
            "Notes:\n" +
            "- Set `needs_skills` to false if the query is casual chat, greeting, or can be answered directly without special skills.\n" +
            "- If `needs_skills` is false, provide the `chat_response` with a helpful direct answer.\n" +
            "- If `needs_skills` is true, `skill_queries` should contain search queries for finding relevant skills.\n";

    /**
     * Fast filtering by name/description only.
     * Ported from ms-agent {@code PROMPT_FILTER_SKILLS_FAST}.
     */
    public static final String PROMPT_FILTER_SKILLS_FAST =
            "Quickly filter candidate skills based on their name and description.\n" +
            "\n" +
            "User Query: {query}\n" +
            "\n" +
            "Candidate Skills:\n" +
            "{candidate_skills}\n" +
            "\n" +
            "For each skill, determine if it's POTENTIALLY relevant to the user's query based on:\n" +
            "1. Does the skill name suggest it can help with the task?\n" +
            "2. Does the skill description indicate capabilities matching the user's needs?\n" +
            "\n" +
            "Output in JSON format:\n" +
            "{\n" +
            "    \"filtered_skill_ids\": [\"skill_id_1\", \"skill_id_2\", ...],\n" +
            "    \"reasoning\": \"Brief explanation of filtering\"\n" +
            "}\n" +
            "\n" +
            "Notes:\n" +
            "- Only include skills that are POTENTIALLY useful for the task.\n" +
            "- This is a quick filter - when in doubt, INCLUDE the skill for further analysis.\n" +
            "- Focus on the main task output format/type matching (e.g., PDF generation needs PDF skill).\n";

    /**
     * Deep filtering by name/description/content.
     * Ported from ms-agent {@code PROMPT_FILTER_SKILLS_DEEP}.
     */
    public static final String PROMPT_FILTER_SKILLS_DEEP =
            "Analyze and filter candidate skills based on their full capabilities.\n" +
            "\n" +
            "User Query: {query}\n" +
            "\n" +
            "Candidate Skills (with detailed content):\n" +
            "{candidate_skills}\n" +
            "\n" +
            "For each skill, evaluate:\n" +
            "1. **Capability Match**: Can this skill actually PRODUCE the required output?\n" +
            "2. **Task Completeness**: Can this skill independently complete the task, or does it need other skills?\n" +
            "3. **Redundancy**: Are there overlapping skills that do the same thing?\n" +
            "\n" +
            "Output in JSON format:\n" +
            "{\n" +
            "    \"filtered_skill_ids\": [\"skill_id_1\", \"skill_id_2\", ...],\n" +
            "    \"skill_analysis\": {\n" +
            "        \"skill_id_1\": {\n" +
            "            \"can_execute\": true/false,\n" +
            "            \"reason\": \"Why this skill can/cannot execute the task\"\n" +
            "        },\n" +
            "        ...\n" +
            "    },\n" +
            "    \"reasoning\": \"Overall filtering explanation\"\n" +
            "}\n" +
            "\n" +
            "**CRITICAL**:\n" +
            "- Only include skills that can ACTUALLY execute and produce the required output.\n" +
            "- Remove redundant skills - keep only the most suitable one for each capability.\n" +
            "- The task specified by the user may require the collaboration of multiple skills to be successfully completed.\n";

    /**
     * Build DAG for filtered skills.
     * Ported from ms-agent {@code PROMPT_BUILD_SKILLS_DAG}.
     */
    public static final String PROMPT_BUILD_SKILLS_DAG =
            "Filter candidate skills and build execution DAG.\n" +
            "\n" +
            "User Query: {query}\n" +
            "\n" +
            "Candidate Skills (USE THESE EXACT IDs in your response):\n" +
            "{selected_skills}\n" +
            "\n" +
            "**Tasks:**\n" +
            "1. **Filter**: Keep only skills that can ACTUALLY produce required output. Remove redundant/unnecessary skills.\n" +
            "2. **Build DAG**: Define dependencies and execution order using the EXACT skill IDs from above (e.g., `pdf@latest`, `pptx@latest`).\n" +
            "\n" +
            "**Output JSON:**\n" +
            "{\n" +
            "    \"filtered_skill_ids\": [\"exact_skill_id_from_list\", ...],\n" +
            "    \"dag\": {\n" +
            "        \"exact_skill_id_1\": [\"depends_on_skill_id\"],\n" +
            "        \"exact_skill_id_2\": []\n" +
            "    },\n" +
            "    \"execution_order\": [\"first_skill_id\", \"second_skill_id\", ...],\n" +
            "    \"reasoning\": \"Brief explanation\"\n" +
            "}\n" +
            "\n" +
            "**CRITICAL RULES:**\n" +
            "- ONLY use exact skill IDs from the Candidate Skills list.\n" +
            "- Minimal sufficiency: smallest skill set that fully satisfies the query\n" +
            "- Deduplicate: keep only the most effective skill when overlapping\n" +
            "- `execution_order` MUST contain ALL skills from `filtered_skill_ids`, ordered by dependencies\n" +
            "- In `dag`, each skill maps to its dependencies (skills it depends on), empty list `[]` means no dependencies\n";
}

