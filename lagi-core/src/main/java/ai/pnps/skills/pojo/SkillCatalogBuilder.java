package ai.pnps.skills.pojo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SkillCatalogBuilder {
    public static final int DEFAULT_MAX_SKILLS = 150;
    public static final int DEFAULT_MAX_PROMPT_CHARS = 30_000;
    public static final int DEFAULT_MAX_DESCRIPTION_CHARS = 400;
    private static final int COMPACT_WARNING_OVERHEAD = 150;

    private SkillCatalogBuilder() {
    }

    public static final class CatalogOptions {
        private final int maxSkillsInPrompt;
        private final int maxSkillsPromptChars;
        private final int maxDescriptionChars;

        public CatalogOptions(int maxSkillsInPrompt, int maxSkillsPromptChars, int maxDescriptionChars) {
            this.maxSkillsInPrompt = maxSkillsInPrompt;
            this.maxSkillsPromptChars = maxSkillsPromptChars;
            this.maxDescriptionChars = maxDescriptionChars;
        }

        public int getMaxSkillsInPrompt() {
            return maxSkillsInPrompt;
        }

        public int getMaxSkillsPromptChars() {
            return maxSkillsPromptChars;
        }

        public int getMaxDescriptionChars() {
            return maxDescriptionChars;
        }
    }

    public static SkillCatalog build(List<SkillEntry> source, CatalogOptions options) {
        if (options == null) {
            return build(source, DEFAULT_MAX_SKILLS, DEFAULT_MAX_PROMPT_CHARS, DEFAULT_MAX_DESCRIPTION_CHARS);
        }
        return build(source, options.getMaxSkillsInPrompt(), options.getMaxSkillsPromptChars(), options.getMaxDescriptionChars());
    }

    public static SkillCatalog build(List<SkillEntry> source, int maxSkills, int maxPromptChars, int maxDescriptionChars) {
        int limitSkills = maxSkills > 0 ? maxSkills : DEFAULT_MAX_SKILLS;
        int limitChars = maxPromptChars > 0 ? maxPromptChars : DEFAULT_MAX_PROMPT_CHARS;
        int limitDesc = maxDescriptionChars > 0 ? maxDescriptionChars : DEFAULT_MAX_DESCRIPTION_CHARS;

        List<SkillEntry> sorted = new ArrayList<SkillEntry>(source);
        sorted.sort(Comparator.comparing(SkillEntry::getKey));
        if (sorted.size() > limitSkills) {
            sorted = new ArrayList<SkillEntry>(sorted.subList(0, limitSkills));
        }
        List<SkillEntry> normalized = new ArrayList<SkillEntry>(sorted.size());
        for (SkillEntry e : sorted) {
            String desc = safeTrim(e.getDescription());
            if (desc.length() > limitDesc) {
                desc = desc.substring(0, Math.max(0, limitDesc - 3)) + "...";
            }
            normalized.add(new SkillEntry(e.getKey(), e.getName(), desc, e.getSkillDir(), e.getSkillMdPath(), e.getFrontmatter()));
        }

        String full = renderFull(normalized);
        if (full.length() <= limitChars) {
            return new SkillCatalog(full, normalized, false, source.size() > normalized.size());
        }
        String compact = renderCompact(normalized);
        int compactBudget = limitChars - COMPACT_WARNING_OVERHEAD;
        if (compact.length() <= compactBudget) {
            String prompt = warningLine(source.size() > normalized.size()) + compact;
            return new SkillCatalog(prompt, normalized, true, source.size() > normalized.size());
        }

        int lo = 0;
        int hi = normalized.size();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String cand = renderCompact(normalized.subList(0, mid));
            if (cand.length() <= compactBudget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        List<SkillEntry> kept = new ArrayList<SkillEntry>(normalized.subList(0, lo));
        String prompt = warningLine(true) + renderCompact(kept);
        return new SkillCatalog(prompt, kept, true, true);
    }

    private static String warningLine(boolean truncated) {
        if (truncated) {
            return "⚠️ Skills truncated: catalog is incomplete (compact format).\n";
        }
        return "⚠️ Skills catalog using compact format (descriptions omitted).\n";
    }

    private static String renderFull(List<SkillEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        sb.append("<available_skills>\n");
        for (SkillEntry e : entries) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(xml(e.getName())).append("</name>\n");
            sb.append("    <description>").append(xml(e.getDescription())).append("</description>\n");
            sb.append("    <location>").append(xml(e.getSkillMdPath().toString())).append("</location>\n");
            sb.append("  </skill>\n");
        }
        sb.append("</available_skills>\n");
        return sb.toString();
    }

    private static String renderCompact(List<SkillEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its name.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        sb.append("<available_skills>\n");
        for (SkillEntry e : entries) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(xml(e.getName())).append("</name>\n");
            sb.append("    <location>").append(xml(e.getSkillMdPath().toString())).append("</location>\n");
            sb.append("  </skill>\n");
        }
        sb.append("</available_skills>\n");
        return sb.toString();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
