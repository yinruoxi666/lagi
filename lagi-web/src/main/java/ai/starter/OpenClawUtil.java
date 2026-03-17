package ai.starter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class OpenClawUtil {

    /**
     * Synchronize configurations for OpenClaw application.
     */
    public static void sync() {
        Map<String, String> providerToModelName = new LinkedHashMap<String, String>();
        providerToModelName.put("fastchat", "fastchat");
        providerToModelName.put("openai", "chatgpt");
        providerToModelName.put("azure", "chatgpt-azure");
        providerToModelName.put("baidu", "ernie");
        providerToModelName.put("zai", "chatglm");
        providerToModelName.put("moonshot", "kimi");
        providerToModelName.put("baichuan", "baichuan");
        providerToModelName.put("volcengine", "spark");
        providerToModelName.put("sense", "SenseChat");
        providerToModelName.put("google", "gemini");
        providerToModelName.put("douban", "doubao");
        providerToModelName.put("anthropic", "claude");

        Path openClawModelsPath = findOpenClawModelsJson();
        if (openClawModelsPath == null || !Files.exists(openClawModelsPath)) {
            return;
        }
        Path lagiYmlPath = resolveLagiYmlPath();
        if (lagiYmlPath == null || !Files.exists(lagiYmlPath)) {
            return;
        }

        String modelsJsonText = readText(openClawModelsPath);
        String lagiYmlText = readText(lagiYmlPath);
        String modelYmlText = resolveAndReadModelYmlText(lagiYmlPath);
        if (modelYmlText == null || modelYmlText.trim().isEmpty()) {
            return;
        }

        JsonObject root = JsonParser.parseString(modelsJsonText).getAsJsonObject();
        JsonObject providers = root.has("providers") && root.get("providers").isJsonObject()
                ? root.getAsJsonObject("providers")
                : new JsonObject();

        List<String> copiedNames = new ArrayList<String>();
        List<String> copiedPreferredModels = new ArrayList<String>();

        for (Map.Entry<String, String> e : providerToModelName.entrySet()) {
            String providerKey = normalizeKey(e.getKey());
            String targetName = normalizeKey(e.getValue());

            JsonObject providerObj = providers.has(providerKey) && providers.get(providerKey).isJsonObject()
                    ? providers.getAsJsonObject(providerKey)
                    : null;
            if (providerObj == null) {
                continue;
            }

            String apiKey = providerObj.has("apiKey") && !providerObj.get("apiKey").isJsonNull()
                    ? providerObj.get("apiKey").getAsString()
                    : "";

            // Keep model.yml as the single source of truth; lagi.yml is patched by copying blocks over.
            YamlBlock modelBlock = extractModelBlockFromModelYml(modelYmlText, targetName);
            if (modelBlock == null) {
                continue;
            }

            String preferredModel = extractFirstModelFromBlock(modelBlock.blockText);
            String updatedBlock = replaceApiKeyInBlock(modelBlock.blockText, apiKey);
            lagiYmlText = upsertModelBlockIntoLagiYml(lagiYmlText, targetName, updatedBlock);

            copiedNames.add(targetName);
            copiedPreferredModels.add(preferredModel);
        }

        if (!copiedNames.isEmpty()) {
            lagiYmlText = updateChatRoute(lagiYmlText, copiedNames);
            lagiYmlText = upsertChatBackends(lagiYmlText, copiedNames, copiedPreferredModels);
        }

        writeText(lagiYmlPath, lagiYmlText);
    }


    private static Path resolveLagiYmlPath() {
        String configured = System.getProperty(ai.starter.Application.CONFIG_FILE_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            Path p = Paths.get(configured.trim());
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return p;
            }
        }
        Path p1 = Paths.get("lagi-web", "src", "main", "resources", "lagi.yml");
        if (Files.exists(p1) && Files.isRegularFile(p1)) return p1;
        Path p2 = Paths.get("src", "main", "resources", "lagi.yml");
        if (Files.exists(p2) && Files.isRegularFile(p2)) return p2;
        return null;
    }


    private static String resolveAndReadModelYmlText(Path lagiYmlPath) {
        Path near = null;
        try {
            Path dir = lagiYmlPath == null ? null : lagiYmlPath.getParent();
            if (dir != null) {
                near = dir.resolve("model.yml");
                if (Files.exists(near) && Files.isRegularFile(near)) {
                    return readText(near);
                }
            }
        } catch (Exception ignore) {
        }

        Path dev1 = Paths.get("lagi-web", "src", "main", "resources", "model.yml");
        if (Files.exists(dev1) && Files.isRegularFile(dev1)) return readText(dev1);
        Path dev2 = Paths.get("src", "main", "resources", "model.yml");
        if (Files.exists(dev2) && Files.isRegularFile(dev2)) return readText(dev2);

        return readClasspathText("/model.yml");
    }

    private static String readClasspathText(String resourcePath) {
        try (InputStream in = OpenClawUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            byte[] bytes = readAllBytesCompat(in);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }


    private static byte[] readAllBytesCompat(InputStream in) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int n;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        while ((n = in.read(buffer)) >= 0) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    private static Path findOpenClawModelsJson() {

        String explicit = System.getenv("OPENCLAW_MODELS_JSON");
        if (explicit != null && !explicit.trim().isEmpty()) {
            Path p = Paths.get(explicit.trim());
            return Files.exists(p) ? p : null;
        }
        String openClawHome = System.getenv("OPENCLAW_HOME");
        if (openClawHome != null && !openClawHome.trim().isEmpty()) {
            Path p = Paths.get(openClawHome.trim(), "agents", "main", "agent", "models.json");
            return Files.exists(p) ? p : null;
        }

        // Prefer the current user's home, but tolerate misconfigured environments on Windows.
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            Path p = Paths.get(userHome.trim(), ".openclaw", "agents", "main", "agent", "models.json");
            if (Files.exists(p)) {
                return p;
            }
        }

        // Fallback: scan all drive roots for a Windows-style Users/* home layout.
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File r : roots) {
                File usersDir = new File(r, "Users");
                if (!usersDir.exists() || !usersDir.isDirectory()) {
                    continue;
                }
                File[] userDirs = usersDir.listFiles();
                if (userDirs == null) {
                    continue;
                }
                for (File u : userDirs) {
                    if (u == null || !u.isDirectory()) {
                        continue;
                    }
                    File models = new File(u, ".openclaw\\agents\\main\\agent\\models.json");
                    if (models.exists() && models.isFile()) {
                        return models.toPath();
                    }
                }
            }
        }

        // If we reach here, OpenClaw is likely not installed/configured for this machine.
        return null;
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim();
    }

    private static String readText(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void writeText(Path path, String text) {
        try {
            Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class YamlBlock {
        final int startLine;
        final int endLineExclusive;
        final String blockText;

        private YamlBlock(int startLine, int endLineExclusive, String blockText) {
            this.startLine = startLine;
            this.endLineExclusive = endLineExclusive;
            this.blockText = blockText;
        }
    }

    private static YamlBlock extractModelBlockFromModelYml(String modelYmlText, String targetName) {
        List<String> lines = splitPreserveNewlines(modelYmlText);

        int nameLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s*-\\s*name\\s*:\\s*" + Pattern.quote(targetName) + "\\s*$")) {
                nameLine = i;
                break;
            }
        }
        if (nameLine < 0) {
            return null;
        }

        // Expand upward to keep leading blank/comment lines with the block for stable diffs.
        int start = nameLine;
        for (int i = nameLine - 1; i >= 0; i--) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s*$") || raw.matches("^\\s*#.*$")) {
                start = i;
                continue;
            }
            break;
        }

        int end = lines.size();
        Pattern nextBlock = Pattern.compile("^\\s*-\\s*name\\s*:\\s*.+$");
        int currentIndent = leadingSpaces(stripLineBreak(lines.get(nameLine)));
        for (int i = nameLine + 1; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.trim().isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            // A sibling "- name:" at the same indentation terminates the current model block.
            if (indent == currentIndent && nextBlock.matcher(raw).matches()) {
                end = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines.get(i));
        }
        return new YamlBlock(start, end, sb.toString());
    }

    private static String replaceApiKeyInBlock(String blockText, String apiKey) {
        // Preserve any trailing inline comment while avoiding "api_key:XXX#comment" concatenation.
        Pattern p = Pattern.compile("(?m)^(\\s*api_key\\s*:\\s*)([^\\r\\n#]*)(\\s*)(#.*)?$");
        Matcher m = p.matcher(blockText);
        if (!m.find()) {
            return blockText;
        }
        String prefix = m.group(1);
        String spacesBeforeComment = m.group(3) == null ? "" : m.group(3);
        String comment = m.group(4);

        String key = apiKey == null ? "" : apiKey.trim();
        if (comment != null && spacesBeforeComment.isEmpty()) {
            spacesBeforeComment = " ";
        }
        String replacedLine = prefix + key + spacesBeforeComment + (comment == null ? "" : comment);
        return blockText.substring(0, m.start()) + replacedLine + blockText.substring(m.end());
    }

    /**
     * 从模型配置块中解析 model 字段的第一个值。
     */
    private static String extractFirstModelFromBlock(String blockText) {
        if (blockText == null || blockText.isEmpty()) {
            return "";
        }

        String lb = detectLineBreak(blockText);
        List<String> lines = splitPreserveNewlines(blockText);

        int modelLineIdx = -1;
        int modelIndent = -1;
        for (int i = 0; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s*model\\s*:\\s*.*$")) {
                modelLineIdx = i;
                modelIndent = leadingSpaces(raw);
                break;
            }
        }
        if (modelLineIdx < 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String firstLine = stripLineBreak(lines.get(modelLineIdx));
        String afterColon = firstLine.substring(firstLine.indexOf(':') + 1);
        sb.append(afterColon);

        for (int i = modelLineIdx + 1; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.trim().isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            // continuations are more indented than the "model:" line; stop at next key
            if (indent <= modelIndent) {
                break;
            }
            if (raw.trim().startsWith("#")) {
                // Ignore comment-only lines inside a multi-line model declaration.
                continue;
            }
            sb.append(" ").append(raw.trim());
        }

        String all = sb.toString();
        int hash = all.indexOf('#');
        if (hash >= 0) {
            all = all.substring(0, hash);
        }
        all = all.replace(lb, " ").trim();
        if (all.isEmpty()) {
            return "";
        }

        String[] parts = all.split(",");
        for (String p : parts) {
            String v = p == null ? "" : p.trim();
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String upsertModelBlockIntoLagiYml(String lagiYmlText, String name, String blockText) {
        List<String> lines = splitPreserveNewlines(lagiYmlText);

        int modelsIdx = indexOfLineStartsWith(lines, "models:");
        if (modelsIdx < 0) {
            return lagiYmlText;
        }
        int includeModelsIdx = indexOfLineStartsWith(lines, "include_models:");
        if (includeModelsIdx < 0) {
            includeModelsIdx = lines.size();
        }

        // Bound the search/update to the models section to avoid accidental cross-section replacements.
        YamlBlock existing = extractBlockFromSectionByName(lines, modelsIdx + 1, includeModelsIdx, name, 2);
        List<String> newBlockLines = splitPreserveNewlines(blockText);

        if (existing != null) {
            List<String> out = new ArrayList<String>(lines.size() - (existing.endLineExclusive - existing.startLine) + newBlockLines.size());
            out.addAll(lines.subList(0, existing.startLine));
            out.addAll(newBlockLines);
            out.addAll(lines.subList(existing.endLineExclusive, lines.size()));
            return joinLines(out);
        }

        int insertAt = includeModelsIdx;
        if (insertAt > 0) {
            String prev = insertAt - 1 < lines.size() ? stripLineBreak(lines.get(insertAt - 1)) : "";
            if (!prev.trim().isEmpty()) {
                newBlockLines = withLeadingBlankLine(newBlockLines, detectLineBreak(lagiYmlText));
            }
        }

        List<String> out = new ArrayList<String>(lines.size() + newBlockLines.size());
        out.addAll(lines.subList(0, insertAt));
        out.addAll(newBlockLines);
        out.addAll(lines.subList(insertAt, lines.size()));
        return joinLines(out);
    }

    private static String updateChatRoute(String lagiYmlText, List<String> names) {
        List<String> lines = splitPreserveNewlines(lagiYmlText);
        int routeIdx = indexOfLineMatches(lines, "^\\s*route\\s*:\\s*.*$");
        if (routeIdx < 0) {
            return lagiYmlText;
        }

        String lb = detectLineBreak(lagiYmlText);
        String raw = stripLineBreak(lines.get(routeIdx));
        int colon = raw.indexOf(':');
        if (colon < 0) {
            return lagiYmlText;
        }
        String prefix = raw.substring(0, colon + 1);
        String expr = raw.substring(colon + 1).trim();

        for (String name : names) {
            if (expr.contains(name)) {
                continue;
            }
            // Heuristic: keep any "(...kimi...)" group intact and only extend that group to avoid changing precedence.
            Pattern groupWithKimi = Pattern.compile("\\(([^)]*\\bkimi\\b[^)]*)\\)");
            Matcher gm = groupWithKimi.matcher(expr);
            if (gm.find()) {
                String inner = gm.group(1);
                String updatedInner = inner + "|" + name;
                expr = expr.substring(0, gm.start(1)) + updatedInner + expr.substring(gm.end(1));
            } else if (expr.endsWith(")")) {
                // If the expression already ends with a group, append inside the group rather than creating a new one.
                expr = expr.substring(0, expr.length() - 1) + "|" + name + ")";
            } else {
                expr = expr + "|" + name;
            }
        }

        lines.set(routeIdx, prefix + " " + expr + lb);
        return joinLines(lines);
    }

    private static String upsertChatBackends(String lagiYmlText, List<String> names, List<String> preferredModels) {
        List<String> lines = splitPreserveNewlines(lagiYmlText);
        String lb = detectLineBreak(lagiYmlText);

        int chatIdx = indexOfLineStartsWith(lines, "  chat:");
        if (chatIdx < 0) {
            return lagiYmlText;
        }
        int backendsIdx = indexOfLineStartsWith(lines, "    backends:");
        if (backendsIdx < 0 || backendsIdx < chatIdx) {
            return lagiYmlText;
        }

        int listStart = backendsIdx + 1;
        int listEnd = recomputeBackendsListEnd(lines, listStart, chatIdx);

        for (int idx = 0; idx < names.size(); idx++) {
            String name = names.get(idx);
            String preferModel = idx < preferredModels.size() ? preferredModels.get(idx) : "";
            if (preferModel == null || preferModel.trim().isEmpty()) {
                preferModel = "default";
            }

            YamlBlock existing = extractBackendBlock(lines, listStart, listEnd, name);
            List<String> backendBlock = Arrays.asList(
                    "      - backend: " + name + lb,
                    "        model: " + preferModel + lb,
                    "        enable: true" + lb,
                    "        stream: true" + lb,
                    "        protocol: completion" + lb,
                    "        priority: 150" + lb,
                    lb
            );

            if (existing != null) {
                // After replacing a slice, recompute indexes because line numbers become stale.
                List<String> out = new ArrayList<String>(lines.size() - (existing.endLineExclusive - existing.startLine) + backendBlock.size());
                out.addAll(lines.subList(0, existing.startLine));
                out.addAll(backendBlock);
                out.addAll(lines.subList(existing.endLineExclusive, lines.size()));
                lines = out;

                backendsIdx = indexOfLineStartsWith(lines, "    backends:");
                listStart = backendsIdx + 1;
                listEnd = recomputeBackendsListEnd(lines, listStart, chatIdx);
            } else {
                int insertAt = listEnd;
                List<String> out = new ArrayList<String>(lines.size() + backendBlock.size());
                out.addAll(lines.subList(0, insertAt));
                out.addAll(backendBlock);
                out.addAll(lines.subList(insertAt, lines.size()));
                lines = out;

                backendsIdx = indexOfLineStartsWith(lines, "    backends:");
                listStart = backendsIdx + 1;
                listEnd = recomputeBackendsListEnd(lines, listStart, chatIdx);
            }
        }

        return joinLines(lines);
    }

    private static int recomputeBackendsListEnd(List<String> lines, int listStart, int chatIdx) {
        int listEnd = lines.size();
        for (int i = listStart; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.trim().isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            if (indent == 4 && raw.matches("^\\s{4}[a-zA-Z0-9_\\-]+\\s*:\\s*.*$")) {
                listEnd = i;
                break;
            }
            if (indent == 2 && raw.matches("^\\s{2}[a-zA-Z0-9_\\-]+\\s*:\\s*.*$") && i > chatIdx) {
                listEnd = i;
                break;
            }
        }
        return listEnd;
    }

    private static YamlBlock extractBackendBlock(List<String> lines, int startInclusive, int endExclusive, String backendName) {
        int backendLine = -1;
        for (int i = startInclusive; i < endExclusive; i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s{6}-\\s*backend\\s*:\\s*" + Pattern.quote(backendName) + "\\s*$")) {
                backendLine = i;
                break;
            }
        }
        if (backendLine < 0) {
            return null;
        }

        int start = backendLine;
        for (int i = backendLine - 1; i >= startInclusive; i--) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s*$")) {
                start = i;
                continue;
            }
            break;
        }

        int end = endExclusive;
        Pattern next = Pattern.compile("^\\s{6}-\\s*backend\\s*:\\s*.+$");
        for (int i = backendLine + 1; i < endExclusive; i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.trim().isEmpty()) {
                continue;
            }
            if (next.matcher(raw).matches()) {
                end = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines.get(i));
        }
        return new YamlBlock(start, end, sb.toString());
    }

    private static YamlBlock extractBlockFromSectionByName(
            List<String> lines,
            int startInclusive,
            int endExclusive,
            String name,
            int expectedIndentForDash
    ) {
        int nameLine = -1;
        String namePattern = "^\\s*-" + "\\s*name\\s*:\\s*" + Pattern.quote(name) + "\\s*$";
        for (int i = startInclusive; i < endExclusive; i++) {
            String raw = stripLineBreak(lines.get(i));
            if (leadingSpaces(raw) == expectedIndentForDash && raw.matches(namePattern)) {
                nameLine = i;
                break;
            }
        }
        if (nameLine < 0) {
            return null;
        }

        int start = nameLine;
        for (int i = nameLine - 1; i >= startInclusive; i--) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.matches("^\\s*$") || raw.matches("^\\s*#.*$")) {
                start = i;
                continue;
            }
            break;
        }

        int end = endExclusive;
        Pattern nextBlock = Pattern.compile("^\\s{" + expectedIndentForDash + "}-\\s*name\\s*:\\s*.+$");
        for (int i = nameLine + 1; i < endExclusive; i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.trim().isEmpty()) {
                continue;
            }
            if (nextBlock.matcher(raw).matches()) {
                end = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines.get(i));
        }
        return new YamlBlock(start, end, sb.toString());
    }

    private static int indexOfLineStartsWith(List<String> lines, String startsWith) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (raw.startsWith(startsWith)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLineMatches(List<String> lines, String regex) {
        Pattern p = Pattern.compile(regex);
        for (int i = 0; i < lines.size(); i++) {
            String raw = stripLineBreak(lines.get(i));
            if (p.matcher(raw).matches()) {
                return i;
            }
        }
        return -1;
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static String detectLineBreak(String text) {
        int idx = text.indexOf("\r\n");
        return idx >= 0 ? "\r\n" : "\n";
    }

    private static String stripLineBreak(String lineWithBreak) {
        if (lineWithBreak.endsWith("\r\n")) {
            return lineWithBreak.substring(0, lineWithBreak.length() - 2);
        }
        if (lineWithBreak.endsWith("\n")) {
            return lineWithBreak.substring(0, lineWithBreak.length() - 1);
        }
        return lineWithBreak;
    }

    private static List<String> splitPreserveNewlines(String text) {
        String lb = detectLineBreak(text);
        List<String> out = new ArrayList<String>();
        int idx = 0;
        while (idx < text.length()) {
            int next = text.indexOf(lb, idx);
            if (next < 0) {
                out.add(text.substring(idx));
                break;
            }
            out.add(text.substring(idx, next + lb.length()));
            idx = next + lb.length();
        }
        if (text.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String s : lines) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static List<String> withLeadingBlankLine(List<String> blockLines, String lb) {
        if (blockLines.isEmpty()) {
            List<String> out = new ArrayList<String>();
            out.add(lb);
            return out;
        }
        String first = stripLineBreak(blockLines.get(0));
        if (first.trim().isEmpty()) {
            return blockLines;
        }
        List<String> out = new ArrayList<String>(blockLines.size() + 1);
        out.add(lb);
        out.addAll(blockLines);
        return out;
    }


}

