package ai.pnps.skill;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Java-side AutoSkills: load skills -> LLM direct select -> build DAG -> execute.
 * <p>
 * This is a lightweight end-to-end implementation to match the ms-agent skill module flow
 * (focused on direct select + direct execution of the first {@code .py} script).
 */
public class AutoSkills {

    @Getter
    private final Map<String, SkillSchema> allSkills;
    private final SkillLlmClient llmClient;
    private final SkillContainer container;
    private final boolean enableRetrieve;

    // Rough defaults aligned with ms-agent intent; since Java side currently uses
    // lexical retrieval (not FAISS/BM25), these values are tuned for that behavior.
    private final int topK;
    private final double minScore;
    private final int maxCandidateSkills;

    private final int maxSkillsInPrompt;
    private final int maxDescriptionLen;

    public AutoSkills(String skillsRoot, SkillLlmClient llmClient, Path workspaceDir, long timeoutSeconds) {
        this(Collections.singletonList(skillsRoot), llmClient, workspaceDir, timeoutSeconds);
    }

    public AutoSkills(List<String> skillsRoots,
                       SkillLlmClient llmClient,
                       Path workspaceDir,
                       long timeoutSeconds) {
        this.allSkills = SkillLoader.loadSkillsStatic(skillsRoots);
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.container = new SkillContainer(workspaceDir, timeoutSeconds);
        this.enableRetrieve = this.allSkills.size() > 10;
        this.topK = 5;
        this.minScore = 0.01;
        this.maxCandidateSkills = 10;
        this.maxSkillsInPrompt = 40;
        this.maxDescriptionLen = 600;
    }

    public SkillDagResult getSkillDag(String query) {
        if (allSkills == null || allSkills.isEmpty()) {
            SkillDagResult res = new SkillDagResult();
            res.setComplete(false);
            res.setClarification("No skills loaded.");
            return res;
        }

        // Direct selection mode: skip retrieval/filtering and ask the model to select DAG directly.
        if (!enableRetrieve) {
            return directSelectSkillDag(query);
        }

        // Retrieval + filter + build DAG mode (matches ms-agent AutoSkills flow).
        return retrieveFilterBuildDag(query);
    }

    public SkillDagResult run(String query, ExecutionInput executionInput, boolean stopOnFailure) {
        SkillDagResult dagResult = getSkillDag(query);

        // chat-only or incomplete: just return plan result.
        if (!dagResult.isComplete() || dagResult.getChatResponse() != null) {
            return dagResult;
        }
        if (dagResult.getExecutionOrder() == null || dagResult.getExecutionOrder().isEmpty()) {
            dagResult.setExecutionResult(new DAGExecutionResult());
            return dagResult;
        }

        DAGExecutor executor = new DAGExecutor(container, allSkills);
        DAGExecutionResult execRes = executor.execute(dagResult.getDag(), dagResult.getExecutionOrder(),
                executionInput, stopOnFailure);
        dagResult.setExecutionResult(execRes);
        return dagResult;
    }

    private SkillDagResult directSelectSkillDag(String query) {
        SkillDagResult res = new SkillDagResult();

        String allSkillsContext = buildAllSkillsContext();
        String prompt = SkillPrompts.PROMPT_DIRECT_SELECT_SKILLS
                .replace("{query}", escapeForPrompt(query))
                .replace("{all_skills}", allSkillsContext);

        String llmText = llmClient.generate(null, prompt);
        JsonNode node = JsonUtils.parseJsonObject(llmText);
        if (node == null) {
            res.setComplete(false);
            res.setClarification("Failed to parse LLM JSON.");
            return res;
        }

        boolean needsSkills = node.path("needs_skills").asBoolean(true);
        String chatResponse = getNullableText(node.get("chat_response"));

        if (!needsSkills) {
            res.setComplete(true);
            res.setChatResponse(chatResponse);
            res.setDag(new LinkedHashMap<String, List<String>>());
            res.setExecutionOrder(Collections.emptyList());
            res.setSelectedSkills(new LinkedHashMap<String, SkillSchema>());
            return res;
        }

        List<String> selectedIds = extractStringArray(node.get("selected_skill_ids"));
        Map<String, SkillSchema> selectedSkills = new LinkedHashMap<String, SkillSchema>();
        for (String sid : selectedIds) {
            if (allSkills.containsKey(sid)) {
                selectedSkills.put(sid, allSkills.get(sid));
            }
        }

        Map<String, List<String>> dag = extractDag(node.get("dag"));
        List<Object> executionOrder = JsonUtils.parseExecutionOrder(node.get("execution_order"));
        if ((executionOrder == null || executionOrder.isEmpty()) && dag != null && !dag.isEmpty()) {
            List<String> topo = SkillDagTopology.topologicalSort(dag);
            executionOrder = new ArrayList<Object>(topo);
        }

        if (dag != null) {
            for (String sid : selectedIds) {
                if (!dag.containsKey(sid)) {
                    dag.put(sid, new ArrayList<String>());
                }
            }
        }

        res.setDag(dag != null ? dag : new LinkedHashMap<String, List<String>>());
        res.setExecutionOrder(executionOrder != null ? executionOrder : Collections.emptyList());
        res.setSelectedSkills(selectedSkills);
        res.setComplete(!selectedSkills.isEmpty());
        if (!res.isComplete()) {
            String reasoning = getNullableText(node.get("reasoning"));
            res.setClarification(reasoning != null ? reasoning : "No relevant skills found.");
        }
        return res;
    }

    private SkillDagResult retrieveFilterBuildDag(String query) {
        SkillDagResult res = new SkillDagResult();

        // Step 1: analyze user query
        String overview = getSkillsOverview(20);
        String analyzePrompt = SkillPrompts.PROMPT_ANALYZE_QUERY_FOR_SKILLS
                .replace("{query}", escapeForPrompt(query))
                .replace("{skills_overview}", overview);
        String analyzeText = llmClient.generate(null, analyzePrompt);
        JsonNode analyzeNode = JsonUtils.parseJsonObject(analyzeText);
        if (analyzeNode == null) {
            res.setComplete(false);
            res.setClarification("Failed to parse analyze JSON.");
            return res;
        }

        boolean needsSkills = analyzeNode.path("needs_skills").asBoolean(true);
        String chatResponse = getNullableText(analyzeNode.get("chat_response"));
        if (!needsSkills) {
            res.setComplete(true);
            res.setChatResponse(chatResponse);
            res.setDag(new LinkedHashMap<String, List<String>>());
            res.setExecutionOrder(Collections.emptyList());
            res.setSelectedSkills(new LinkedHashMap<String, SkillSchema>());
            return res;
        }

        List<String> skillQueries = extractStringArray(analyzeNode.get("skill_queries"));
        if (skillQueries == null || skillQueries.isEmpty()) {
            skillQueries = Collections.singletonList(query);
        }

        // Step 2: retrieval (lexical fallback)
        java.util.Set<String> collected = retrieveSkillIds(skillQueries);
        if (collected == null || collected.isEmpty()) {
            res.setComplete(false);
            res.setClarification("No relevant skills found. Please provide more details.");
            return res;
        }

        // Limit candidate skills
        if (collected.size() > maxCandidateSkills) {
            java.util.List<String> ids = new java.util.ArrayList<String>(collected);
            collected = new java.util.HashSet<String>(ids.subList(0, maxCandidateSkills));
        }

        // Step 3: fast filter
        java.util.Set<String> afterFast = filterSkills(query, collected, "fast");
        if (afterFast == null || afterFast.isEmpty()) {
            res.setComplete(false);
            res.setClarification("No relevant skills found after filtering. Please refine your query.");
            return res;
        }

        // deep filter (only if > 1)
        java.util.Set<String> finalCandidates = afterFast;
        if (finalCandidates.size() > 1) {
            finalCandidates = filterSkills(query, finalCandidates, "deep");
            if (finalCandidates == null || finalCandidates.isEmpty()) {
                res.setComplete(false);
                res.setClarification("No relevant skills found after filtering. Please refine your query.");
                return res;
            }
        }

        // Step 4: build DAG
        DagBuildResult dagBuild = buildDag(query, finalCandidates);
        if (dagBuild == null || dagBuild.filteredSkillIds == null || dagBuild.filteredSkillIds.isEmpty()) {
            res.setComplete(false);
            res.setClarification("No relevant skills found after filtering. Please refine your query.");
            return res;
        }

        Map<String, SkillSchema> selectedSkills = new LinkedHashMap<String, SkillSchema>();
        for (String sid : dagBuild.filteredSkillIds) {
            SkillSchema s = allSkills.get(sid);
            if (s != null) {
                selectedSkills.put(sid, s);
            }
        }

        res.setDag(dagBuild.dag);
        res.setExecutionOrder(dagBuild.executionOrder);
        res.setSelectedSkills(selectedSkills);
        res.setComplete(true);
        return res;
    }

    private String getSkillsOverview(int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, SkillSchema> e : allSkills.entrySet()) {
            if (count >= limit) {
                break;
            }
            String skillId = e.getKey();
            SkillSchema s = e.getValue();
            String desc = s.getDescription() != null ? s.getDescription() : "";
            if (desc.length() > 200) {
                desc = desc.substring(0, 200);
            }
            sb.append("- [").append(skillId).append("] ")
                    .append(s.getName() != null ? s.getName() : "")
                    .append(": ").append(desc).append("\n");
            count++;
        }
        return sb.toString();
    }

    private java.util.Set<String> retrieveSkillIds(List<String> queries) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (queries == null || queries.isEmpty()) {
            return out;
        }
        for (String q : queries) {
            if (q == null) {
                continue;
            }
            java.util.List<ScoredSkill> scored = new java.util.ArrayList<ScoredSkill>();
            for (Map.Entry<String, SkillSchema> e : allSkills.entrySet()) {
                String sid = e.getKey();
                SkillSchema s = e.getValue();
                String doc = (s.getName() != null ? s.getName() : "") + " " + (s.getDescription() != null ? s.getDescription() : "");
                double score = overlapScore(q, doc);
                scored.add(new ScoredSkill(sid, score));
            }
            // sort by score desc
            java.util.Collections.sort(scored, new java.util.Comparator<ScoredSkill>() {
                @Override
                public int compare(ScoredSkill o1, ScoredSkill o2) {
                    return Double.compare(o2.score, o1.score);
                }
            });
            int picked = 0;
            for (ScoredSkill s : scored) {
                if (picked >= topK) {
                    break;
                }
                if (s.score >= minScore) {
                    out.add(s.skillId);
                    picked++;
                }
            }
        }
        return out;
    }

    private static class ScoredSkill {
        String skillId;
        double score;
        ScoredSkill(String skillId, double score) {
            this.skillId = skillId;
            this.score = score;
        }
    }

    private double overlapScore(String query, String doc) {
        if (query == null || doc == null) {
            return 0.0;
        }
        java.util.Set<String> qt = tokenize(query);
        java.util.Set<String> dt = tokenize(doc);
        if (qt.isEmpty() || dt.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String t : qt) {
            if (dt.contains(t)) {
                overlap++;
            }
        }
        // cosine-like normalization (no external deps)
        double denom = Math.sqrt((double) qt.size() * (double) dt.size());
        if (denom <= 0.0) {
            return 0.0;
        }
        return overlap / denom;
    }

    private java.util.Set<String> tokenize(String s) {
        java.util.Set<String> tokens = new java.util.HashSet<String>();
        if (s == null) {
            return tokens;
        }
        // word sequences OR single CJK characters
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[A-Za-z0-9]+|[\\u4e00-\\u9fff]");
        java.util.regex.Matcher m = p.matcher(s.toLowerCase(java.util.Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (t != null && !t.trim().isEmpty()) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private java.util.Set<String> filterSkills(String query, java.util.Set<String> skillIds, String mode) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new java.util.HashSet<String>();
        }
        if (skillIds.size() <= 1) {
            return new java.util.HashSet<String>(skillIds);
        }

        String prompt;
        if ("deep".equals(mode)) {
            java.util.List<String> entries = new java.util.ArrayList<String>();
            for (String sid : skillIds) {
                SkillSchema s = allSkills.get(sid);
                if (s == null) {
                    continue;
                }
                String content = s.getContent() != null ? s.getContent() : "";
                if (content.length() > 3000) {
                    content = content.substring(0, 3000);
                }
                String entry = "### [" + sid + "] " + (s.getName() != null ? s.getName() : "") + "\n" +
                        "**Description**: " + (s.getDescription() != null ? s.getDescription() : "") + "\n" +
                        "**Content**: " + content;
                entries.add(entry);
            }
            String candidateSkillsText = join(entries, "\n\n");
            prompt = SkillPrompts.PROMPT_FILTER_SKILLS_DEEP
                    .replace("{query}", escapeForPrompt(query))
                    .replace("{candidate_skills}", candidateSkillsText);
        } else {
            java.util.List<String> lines = new java.util.ArrayList<String>();
            for (String sid : skillIds) {
                SkillSchema s = allSkills.get(sid);
                if (s == null) {
                    continue;
                }
                lines.add("- [" + sid + "] " + (s.getName() != null ? s.getName() : "") + ": " + (s.getDescription() != null ? s.getDescription() : ""));
            }
            String candidateSkillsText = join(lines, "\n");
            prompt = SkillPrompts.PROMPT_FILTER_SKILLS_FAST
                    .replace("{query}", escapeForPrompt(query))
                    .replace("{candidate_skills}", candidateSkillsText);
        }

        String llmText = llmClient.generate(null, prompt);
        JsonNode node = JsonUtils.parseJsonObject(llmText);
        if (node == null) {
            return new java.util.HashSet<String>(skillIds);
        }

        java.util.Set<String> filtered = new java.util.HashSet<String>();
        java.util.List<String> ids = extractStringArray(node.get("filtered_skill_ids"));
        if (ids == null || ids.isEmpty()) {
            ids = new java.util.ArrayList<String>(skillIds);
        }
        for (String id : ids) {
            filtered.add(id);
        }

        if ("deep".equals(mode)) {
            JsonNode analysis = node.get("skill_analysis");
            if (analysis != null && analysis.isObject()) {
                java.util.List<String> keep = new java.util.ArrayList<String>();
                for (String sid : filtered) {
                    JsonNode one = analysis.get(sid);
                    if (one == null || one.isNull()) {
                        keep.add(sid);
                        continue;
                    }
                    boolean canExecute = one.path("can_execute").asBoolean(true);
                    if (canExecute) {
                        keep.add(sid);
                    }
                }
                filtered.clear();
                filtered.addAll(keep);
            }
        }

        return filtered;
    }

    private static class DagBuildResult {
        java.util.Set<String> filteredSkillIds;
        Map<String, List<String>> dag;
        List<Object> executionOrder;
        DagBuildResult() {}
    }

    private DagBuildResult buildDag(String query, java.util.Set<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return null;
        }

        List<String> entries = new ArrayList<String>();
        for (String sid : skillIds) {
            SkillSchema s = allSkills.get(sid);
            if (s == null) {
                continue;
            }
            String content = s.getContent() != null ? s.getContent() : "";
            if (content.length() > 3000) {
                content = content.substring(0, 3000);
            }
            entries.add("- [" + sid + "] " + (s.getName() != null ? s.getName() : "") + "\n" +
                    "  " + (s.getDescription() != null ? s.getDescription() : "") + "\n" +
                    " Main Content: " + content);
        }
        String skillsInfo = join(entries, "\n");
        String prompt = SkillPrompts.PROMPT_BUILD_SKILLS_DAG
                .replace("{query}", escapeForPrompt(query))
                .replace("{selected_skills}", skillsInfo);

        String llmText = llmClient.generate(null, prompt);
        JsonNode node = JsonUtils.parseJsonObject(llmText);
        if (node == null) {
            DagBuildResult r = new DagBuildResult();
            r.filteredSkillIds = new java.util.HashSet<String>(skillIds);
            r.dag = new LinkedHashMap<String, List<String>>();
            for (String sid : skillIds) {
                r.dag.put(sid, new ArrayList<String>());
            }
            r.executionOrder = new ArrayList<Object>(skillIds);
            return r;
        }

        java.util.Set<String> filtered = new java.util.HashSet<String>();
        List<String> filteredIds = extractStringArray(node.get("filtered_skill_ids"));
        if (filteredIds == null || filteredIds.isEmpty()) {
            filteredIds = new ArrayList<String>(skillIds);
        }
        for (String sid : filteredIds) {
            if (skillIds.contains(sid)) {
                filtered.add(sid);
            }
        }
        if (filtered.isEmpty()) {
            filtered.addAll(skillIds);
        }

        Map<String, List<String>> dag = extractDag(node.get("dag"));
        if (dag == null) {
            dag = new LinkedHashMap<String, List<String>>();
        }
        for (String sid : filtered) {
            if (!dag.containsKey(sid)) {
                dag.put(sid, new ArrayList<String>());
            }
        }

        List<Object> executionOrder = JsonUtils.parseExecutionOrder(node.get("execution_order"));
        if (executionOrder == null || executionOrder.isEmpty()) {
            List<String> topo = SkillDagTopology.topologicalSort(dag);
            executionOrder = new ArrayList<Object>(topo);
        }

        DagBuildResult r = new DagBuildResult();
        r.filteredSkillIds = filtered;
        r.dag = dag;
        r.executionOrder = executionOrder;
        return r;
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private List<String> extractStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                out.add(item.asText());
            }
        }
        return out;
    }

    private Map<String, List<String>> extractDag(JsonNode dagNode) {
        Map<String, List<String>> dag = new LinkedHashMap<String, List<String>>();
        if (dagNode == null || !dagNode.isObject()) {
            return dag;
        }
        for (java.util.Iterator<Map.Entry<String, JsonNode>> it = dagNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            List<String> deps = new ArrayList<String>();
            JsonNode depNode = e.getValue();
            if (depNode != null && depNode.isArray()) {
                for (JsonNode dep : depNode) {
                    if (dep != null && dep.isTextual()) {
                        deps.add(dep.asText());
                    }
                }
            }
            dag.put(key, deps);
        }
        return dag;
    }

    private String buildAllSkillsContext() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, SkillSchema> e : allSkills.entrySet()) {
            if (count >= maxSkillsInPrompt) {
                break;
            }
            String sid = e.getKey();
            SkillSchema s = e.getValue();
            String desc = s.getDescription() != null ? s.getDescription() : "";
            if (desc.length() > maxDescriptionLen) {
                desc = desc.substring(0, maxDescriptionLen);
            }
            sb.append("- [").append(sid).append("] ")
                    .append(s.getName() != null ? s.getName() : "")
                    .append("\n  ").append(desc).append("\n");
            count++;
        }
        return sb.toString();
    }

    private String escapeForPrompt(String s) {
        return s != null ? s : "";
    }

    private String getNullableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}

