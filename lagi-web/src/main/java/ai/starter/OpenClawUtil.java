package ai.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
public class OpenClawUtil {

    private static final String OPEN_CLAW_DIR_NAME = ".openclaw";
    private static final String[] MODELS_JSON_PATH_SEGMENTS = {"agents", "main", "agent", "models.json"};
    private static final String CONFIG_FILE_PROPERTY = Application.CONFIG_FILE_PROPERTY;
    private static final String YAML_INDENT = "  ";
    private static final String YAML_COMMENT_PREFIX = "#";


    public static void sync(int port) {
        syncToOpenClaw(port);
    }

    public static void syncToOpenClaw(int port) {
        OpenClawInjector.inject("http://127.0.0.1:" + port + "/v1");
    }

    public static void syncToLinkMind() {
        try {
            Path lagiYmlPath = getLagiYmlPath();
            if (lagiYmlPath == null || !Files.exists(lagiYmlPath)) {
                return;
            }

            // 读取并解析 OpenClaw 的 models.json
            JsonNode providersNode = readAndParseModelsJson();
            if (providersNode == null) {
                return;
            }

            OpenClawFragmentsResult fragmentsResult = generateOpenClawFragments(providersNode);
            if (fragmentsResult == null || fragmentsResult.fragments.isEmpty()) {
                log.warn("No OpenClaw models to sync, skip");
                return;
            }

            // 4) 写到lagi.yml
            String originalYaml = readFileContent(lagiYmlPath);
            String updatedYaml = injectFragmentsIntoYaml(originalYaml, fragmentsResult.fragments, fragmentsResult.selectedModels);

            if (originalYaml.equals(updatedYaml)) {
                log.info("No changes needed, lagi.yml already contains OpenClaw configuration");
                return;
            }

            writeFileAtomic(lagiYmlPath, updatedYaml);
            log.info("Successfully synced OpenClaw configuration to {}", lagiYmlPath);

        } catch (Exception e) {
            log.error("Failed to sync OpenClaw config", e);
        }
    }


    private static Path getLagiYmlPath() {
        String configFilePath = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFilePath == null || configFilePath.trim().isEmpty()) {
            return null;
        }
        return Paths.get(configFilePath);
    }


    private static JsonNode readAndParseModelsJson() throws IOException {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            log.warn("Cannot get user home directory");
            return null;
        }

        Path openClawDir = Paths.get(userHome, OPEN_CLAW_DIR_NAME);
        if (!Files.isDirectory(openClawDir)) {
            log.warn("OpenClaw directory does not exist: {}", openClawDir);
            return null;
        }

        Path modelsJsonPath = openClawDir;
        for (String segment : MODELS_JSON_PATH_SEGMENTS) {
            modelsJsonPath = modelsJsonPath.resolve(segment);
        }

        if (!Files.exists(modelsJsonPath)) {
            log.warn("models.json does not exist: {}", modelsJsonPath);
            return null;
        }

        String content = readFileContent(modelsJsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode providersNode = root.get("providers");

        return (providersNode != null && providersNode.isObject()) ? providersNode : null;
    }


    private static String readFileContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // 写临时文件后 move 覆盖目标文件
    private static void writeFileAtomic(Path path, String content) throws IOException {
        Path tempFile = Files.createTempFile(path.getParent(), "lagi_openclaw_temp_", ".yml");
        try {
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // models.json 的 providers 与 model.yml 做匹配
    @SuppressWarnings("unchecked")
    private static OpenClawFragmentsResult generateOpenClawFragments(JsonNode providersNode) throws IOException {

        Path lagiYmlPath = getLagiYmlPath();
        if (lagiYmlPath == null) {
            throw new IOException("Cannot resolve lagi.yml path for locating model.yml");
        }
        Path modelYmlPath = lagiYmlPath.getParent().resolve("model.yml");
        if (!Files.exists(modelYmlPath)) {
            throw new IOException("model.yml not found at: " + modelYmlPath);
        }

        YAMLMapper yamlMapper = createYamlMapper();
        Map<String, Object> modelRoot = yamlMapper.readValue(Files.newInputStream(modelYmlPath), Map.class);
        List<Map<String, Object>> allModelDefs = (List<Map<String, Object>>) modelRoot.get("models");
        if (allModelDefs == null || allModelDefs.isEmpty()) {
            throw new IOException("No models defined in model.yml");
        }

        // 根据 models.json 的 providers 去匹配 model.yml 中的模型块
        List<Map<String, Object>> selectedModels = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> providerIterator = providersNode.fields();
        while (providerIterator.hasNext()) {
            Map.Entry<String, JsonNode> providerEntry = providerIterator.next();
            String providerName = providerEntry.getKey();
            JsonNode providerNode = providerEntry.getValue();
            if (providerName == null || providerName.trim().isEmpty() || providerNode == null || providerNode.isNull()) {
                continue;
            }

            JsonNode modelsNode = providerNode.get("models");
            if (modelsNode == null || !modelsNode.isArray() || modelsNode.size() == 0) {
                continue;
            }

            // 使用该 provider 下的第一个 model 进行匹配
            JsonNode firstModelNode = modelsNode.get(0);
            if (firstModelNode == null || firstModelNode.isNull()) {
                continue;
            }

            Map<String, Object> matched = findMatchingModelDef(allModelDefs, providerName, firstModelNode);
            if (matched == null) {
                log.warn("No matching model found in model.yml for provider: {}, skip", providerName);
                continue;
            }

            // 避免同一个 name 重复写入
            String name = matched.get("name") == null ? null : matched.get("name").toString();
            if (name != null && usedNames.contains(name)) {
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>();

            if (matched.get("name") != null) {
                copy.put("name", matched.get("name"));
            }
            if (matched.get("type") != null) {
                copy.put("type", matched.get("type"));
            }

            if (matched.get("model") != null) {
                copy.put("model", matched.get("model"));
            }
            // 保留 driver 字段，如果有的话
            if (matched.get("driver") != null) {
                copy.put("driver", matched.get("driver"));
            }

            copy.put("enable", Boolean.TRUE);

            String baseUrl = getNodeTextValue(providerNode, "baseUrl");
            String apiKey = getNodeTextValue(providerNode, "apiKey");
            if (baseUrl != null && !baseUrl.isEmpty()) {
                copy.put("api_address", baseUrl);
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                copy.put("api_key", apiKey);
            }

            selectedModels.add(copy);
            if (name != null) {
                usedNames.add(name);
            }
        }

        if (selectedModels.isEmpty()) {
            log.warn("No matching models found in model.yml for providers.json, skip sync");
            return null;
        }


        // models 和 backends
        StringBuilder modelsFragment = new StringBuilder();
        StringBuilder backendsFragment = new StringBuilder();

        boolean isFirstBackend = true;
        for (Map<String, Object> m : selectedModels) {
            String name = m.get("name") == null ? "" : m.get("name").toString();
            String type = m.get("type") == null ? "" : m.get("type").toString();
            String modelField = m.get("model") == null ? "" : m.get("model").toString();
            String driver = m.get("driver") == null ? "" : m.get("driver").toString();
            String apiAddress = m.get("api_address") == null ? "" : m.get("api_address").toString();
            String apiKey = m.get("api_key") == null ? "" : m.get("api_key").toString();

            // 追加到 models下的内容
            modelsFragment.append(YAML_INDENT).append("- name: ").append(name).append(System.lineSeparator());
            if (!type.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("type: ").append(type).append(System.lineSeparator());
            }
            if (!modelField.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("model: ").append(modelField).append(System.lineSeparator());
            }
            if (!driver.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("driver: ").append(driver).append(System.lineSeparator());
            }
            modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("enable: true").append(System.lineSeparator());
            if (!apiAddress.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("api_address: ").append(apiAddress).append(System.lineSeparator());
            }
            if (!apiKey.isEmpty()) {
                modelsFragment.append(YAML_INDENT).append(YAML_INDENT).append("api_key: ").append(apiKey).append(System.lineSeparator());
            }

            // 追加到 functions.chat.backends 下的内容

            if (!isFirstBackend) {
                backendsFragment.append(System.lineSeparator());
            }
            backendsFragment.append(repeatIndent(3))
                    .append("- backend: ").append(name).append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("model: ").append(modelField).append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("enable: true").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("stream: true").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("protocol: completion").append(System.lineSeparator());
            backendsFragment.append(repeatIndent(4))
                    .append("priority: 100").append(System.lineSeparator());

            isFirstBackend = false;
        }

        return new OpenClawFragmentsResult(
                new OpenClawFragments(modelsFragment.toString(), backendsFragment.toString()),
                selectedModels
        );
    }

    // 避免重复写入
    private static String injectFragmentsIntoYaml(String originalYaml, OpenClawFragments fragments,
                                                  List<Map<String, Object>> selectedModels) {
        String result = originalYaml;

        result = injectModelsFragment(result, fragments.modelsFragment);

        result = injectBackendsFragment(result, fragments.backendsFragment, selectedModels);

        result = injectChatRoute(result, selectedModels);

        return result;
    }

    // 把本次同步的 backend 名称补到 functions.chat.route
    private static String injectChatRoute(String yaml, List<Map<String, Object>> selectedModels) {
        if (selectedModels == null || selectedModels.isEmpty()) {
            return yaml;
        }

        List<String> backendNames = new ArrayList<>();
        for (Map<String, Object> m : selectedModels) {
            Object nameObj = m == null ? null : m.get("name");
            if (nameObj == null) continue;
            String name = nameObj.toString().trim();
            if (!name.isEmpty() && !backendNames.contains(name)) {
                backendNames.add(name);
            }
        }
        if (backendNames.isEmpty()) {
            return yaml;
        }

        String[] lines = yaml.split("\\r?\\n", -1);
        int functionsLine = findTopLevelLine(lines, "functions:");
        if (functionsLine < 0) {
            return yaml;
        }

        int chatLine = findChatLineUnderFunctions(lines, functionsLine);
        if (chatLine < 0) {
            return yaml;
        }

        // 在 functions.chat 这个块内找 route
        int chatEnd = findBlockEnd(lines, chatLine);
        String routeIndent = YAML_INDENT + YAML_INDENT; // 4 spaces
        List<Integer> routeLines = new ArrayList<>();

        for (int i = chatLine + 1; i < chatEnd; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            if (line.startsWith(routeIndent) && !line.startsWith(routeIndent + YAML_INDENT) && trimmed.startsWith("route:")) {
                routeLines.add(i);
            }
        }

        if (!routeLines.isEmpty()) {
            // 更新第一条 route，并删除其余重复 route
            int firstRouteLine = routeLines.get(0);
            String oldRoute = lines[firstRouteLine].trim().substring("route:".length()).trim();
            String newRoute = updateBestRoute(oldRoute, backendNames);
            if (!newRoute.equals(oldRoute)) {
                lines[firstRouteLine] = routeIndent + "route: " + newRoute;
            }

            if (routeLines.size() > 1) {

                java.util.Set<Integer> toRemove = new java.util.HashSet<>(routeLines.subList(1, routeLines.size()));
                List<String> newList = new ArrayList<>(lines.length);
                for (int i = 0; i < lines.length; i++) {
                    if (!toRemove.contains(i)) {
                        newList.add(lines[i]);
                    }
                }
                return joinLines(newList.toArray(new String[0]));
            }
            return joinLines(lines);
        }

        // 没有 route:，在 chat: 下插入一行 route
        int insertPos = chatLine + 1;
        // 跳过 chat: 下方的空行/注释，保证 route 在最前面（风格一致）
        while (insertPos < chatEnd) {
            String t = lines[insertPos].trim();
            if (t.isEmpty() || t.startsWith(YAML_COMMENT_PREFIX)) {
                insertPos++;
                continue;
            }
            break;
        }

        String[] newLines = new String[lines.length + 1];
        System.arraycopy(lines, 0, newLines, 0, insertPos);
        newLines[insertPos] = routeIndent + "route: " + updateBestRoute("", backendNames);
        System.arraycopy(lines, insertPos, newLines, insertPos + 1, lines.length - insertPos);
        return joinLines(newLines);
    }

    // 把 backendNames 补到第二个分组
    private static String updateBestRoute(String routeExpr, List<String> backendNames) {
        if (backendNames == null || backendNames.isEmpty()) {
            return routeExpr == null ? "" : routeExpr;
        }
        String route = routeExpr == null ? "" : routeExpr.trim();

        // 没有 route 或不是 best(...)，给一个最简单的 best( (a|b|c) )
        if (route.isEmpty() || !route.startsWith("best(") || !route.endsWith(")")) {
            return "best((" + String.join("|", backendNames) + "))";
        }

        String inside = route.substring("best(".length(), route.length() - 1);
        int depth = 0;
        int splitAt = -1;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                splitAt = i;
                break;
            }
        }

        if (splitAt < 0) {
            String updated = addNamesToGroup(inside.trim(), backendNames);
            return "best(" + updated + ")";
        }

        String arg1 = inside.substring(0, splitAt).trim();
        String arg2 = inside.substring(splitAt + 1).trim();
        String updatedArg2 = addNamesToGroup(arg2, backendNames);
        if (updatedArg2.equals(arg2)) {
            return route;
        }
        return "best(" + arg1 + "," + updatedArg2 + ")";
    }

    // 在分组里追加缺失 backend 名称
    private static String addNamesToGroup(String groupExpr, List<String> backendNames) {
        String g = groupExpr == null ? "" : groupExpr.trim();
        boolean wrapped = g.startsWith("(") && g.endsWith(")");
        String inner = wrapped ? g.substring(1, g.length() - 1).trim() : g;

        List<String> tokens = new ArrayList<>();
        if (!inner.isEmpty()) {
            // 只按 '|' 拆
            for (String t : inner.split("\\|")) {
                String tt = t.trim();
                if (!tt.isEmpty() && !tokens.contains(tt)) {
                    tokens.add(tt);
                }
            }
        }

        boolean changed = false;
        for (String name : backendNames) {
            if (name == null || name.trim().isEmpty()) continue;
            if (!tokens.contains(name)) {
                tokens.add(name);
                changed = true;
            }
        }

        if (!changed) {
            return groupExpr == null ? "" : groupExpr.trim();
        }

        String rebuilt = String.join("|", tokens);
        return wrapped ? "(" + rebuilt + ")" : rebuilt;
    }

    private static int findTopLevelLine(String[] lines, String exactKey) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (exactKey.equals(trimmed) && line.length() == trimmed.length()) {
                return i;
            }
        }
        return -1;
    }

    private static int findChatLineUnderFunctions(String[] lines, int functionsLine) {
        int functionsIndent = lines[functionsLine].length() - lines[functionsLine].trim().length(); // should be 0
        for (int i = functionsLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            int indent = line.length() - trimmed.length();
            if (indent <= functionsIndent) {
                return -1;
            }

            if (indent == YAML_INDENT.length() && "chat:".equals(trimmed)) {
                return i;
            }
        }
        return -1;
    }

    // 查找block 的结束行
    private static int findBlockEnd(String[] lines, int startLine) {
        int baseIndent = lines[startLine].length() - lines[startLine].trim().length();
        for (int i = startLine + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            int indent = lines[i].length() - trimmed.length();
            if (indent <= baseIndent) {
                return i;
            }
        }
        return lines.length;
    }

    private static String joinLines(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append(System.lineSeparator());
        }
        return sb.toString().trim() + System.lineSeparator();
    }

    // 注入 models
    private static String injectModelsFragment(String yaml, String modelsFragment) {
        String[] lines = yaml.split("\\r?\\n", -1);
        int modelsStartLine = findModelsStartLine(lines);

        // 在末尾直接创建并写入全部片段
        if (modelsStartLine < 0) {
            StringBuilder sb = new StringBuilder();
            appendLines(sb, lines, 0, lines.length);
            sb.append(System.lineSeparator());
            sb.append("models:").append(System.lineSeparator());
            sb.append(modelsFragment);
            return sb.toString().trim() + System.lineSeparator();
        }

        // 现有 models 下已经存在的 name 列表
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (int i = modelsStartLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            // 遇到非缩进行，说明 models 节点结束
            if (!line.startsWith(YAML_INDENT)) {
                break;
            }
            if (trimmed.startsWith("- name:")) {
                String name = trimmed.substring("- name:".length()).trim();
                if (!name.isEmpty()) {
                    existingNames.add(name);
                }
            }
        }

        //将已经存在的 name 对应的块剔除，只保留新model
        String[] fragLines = modelsFragment.split("\\r?\\n", -1);
        StringBuilder filteredFragment = new StringBuilder();
        List<String> currentBlock = new ArrayList<>();
        String currentName = null;

        java.util.function.BiConsumer<String, List<String>> flushBlock = (name, blockLines) -> {
            if (name == null || blockLines.isEmpty()) return;
            if (existingNames.contains(name)) {
                // 已经存在的 name，跳过，不再写入，避免重复
                return;
            }
            if (filteredFragment.length() > 0) {
                filteredFragment.append(System.lineSeparator());
            }
            for (int i = 0; i < blockLines.size(); i++) {
                filteredFragment.append(blockLines.get(i));
                if (i < blockLines.size() - 1) {
                    filteredFragment.append(System.lineSeparator());
                }
            }
        };

        for (String line : fragLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- name:")) {
                flushBlock.accept(currentName, currentBlock);
                currentBlock = new ArrayList<>();
                currentBlock.add(line);
                currentName = trimmed.substring("- name:".length()).trim();
            } else {
                if (!currentBlock.isEmpty()) {
                    currentBlock.add(line);
                }
            }
        }
        // 处理最后一个 block
        flushBlock.accept(currentName, currentBlock);

        if (filteredFragment.length() == 0) {
            return yaml;
        }

        // 在 models 段的末尾追加新model块
        int insertPos = findModelsInsertPosition(lines, modelsStartLine);
        StringBuilder sb = new StringBuilder();
        appendLines(sb, lines, 0, insertPos);
        if (insertPos > modelsStartLine + 1) {
            sb.append(System.lineSeparator());
        }
        sb.append(filteredFragment);
        appendLines(sb, lines, insertPos, lines.length);
        return sb.toString().trim() + System.lineSeparator();
    }

    private static String repeatIndent(int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(YAML_INDENT);
        }
        return sb.toString();
    }

    // backend 已存在则替换，否则追加
    private static String injectBackendsFragment(String yaml, String backendsFragment,
                                                 List<Map<String, Object>> selectedModels) {
        String[] lines = yaml.split("\\r?\\n", -1);
        int backendsLine = findBackendsLine(lines);

        if (backendsLine < 0) {
            // 没找到 backends，需要创建 functions.chat.backends 结构
            return createBackendsSection(yaml, backendsFragment, lines);
        }

        // 找到了 backends，进行替换或追加
        return replaceOrAppendBackends(lines, backendsLine, backendsFragment, selectedModels);
    }

    // 精准查找 functions/chat/backends
    private static int findBackendsLine(String[] lines) {
        int functionsLine = -1;
        int chatLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            // 必须精确匹配 "functions:"，且无缩进（顶级节点）
            if ("functions:".equals(trimmed) && line.length() == trimmed.length()) {
                functionsLine = i;
                break;
            }
        }
        if (functionsLine < 0) {
            return -1;
        }

        for (int i = functionsLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            // 必须精确匹配 "chat:"，且缩进为2空格（YAML_INDENT）
            if ("chat:".equals(trimmed) && line.startsWith(YAML_INDENT) &&
                    !line.startsWith(YAML_INDENT + YAML_INDENT)) {
                chatLine = i;
                break;
            }
            if (!line.startsWith(YAML_INDENT) && !line.trim().isEmpty()) {
                return -1;
            }
        }
        if (chatLine < 0) {
            return -1;
        }

        for (int i = chatLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            // 必须精确匹配 "backends:"，且缩进为4空格（YAML_INDENT * 2）
            if ("backends:".equals(trimmed) && line.startsWith(YAML_INDENT + YAML_INDENT) &&
                    !line.startsWith(YAML_INDENT + YAML_INDENT + YAML_INDENT)) {
                return i;
            }
            if (!line.startsWith(YAML_INDENT + YAML_INDENT) && !line.trim().isEmpty()) {
                return -1;
            }
        }
        return -1;
    }

    // 创建 functions.chat.backends
    private static String createBackendsSection(String yaml, String backendsFragment, String[] lines) {
        int functionsLine = -1;
        // 查找 functions:（顶级节点，0缩进）
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if ("functions:".equals(trimmed) && line.length() == trimmed.length()) {
                functionsLine = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (functionsLine >= 0) {
            // 有 functions:，检查是否有 chat
            int chatLine = -1;
            int chatEndPos = -1;

            for (int i = functionsLine + 1; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();
                if ("chat:".equals(trimmed) && line.startsWith(YAML_INDENT) &&
                        !line.startsWith(YAML_INDENT + YAML_INDENT)) {
                    chatLine = i;
                    // 修复：正确查找 chat 节点的结束位置（子节点缩进必须大于 chat 的缩进）
                    chatEndPos = findChatSectionEnd(lines, chatLine);
                    break;
                }
                // 如果遇到非缩进行,说明没找到
                if (!line.startsWith(YAML_INDENT) && !line.trim().isEmpty()) {
                    break;
                }
            }

            if (chatLine >= 0 && chatEndPos > 0) {
                // 有 chat:，在 chat 节点末尾添加 backends:
                appendLines(sb, lines, 0, chatEndPos);
                // 换行后添加 backends
                sb.append(System.lineSeparator());
                sb.append(YAML_INDENT + YAML_INDENT).append("backends:").append(System.lineSeparator());
                sb.append(backendsFragment);
                appendLines(sb, lines, chatEndPos, lines.length);
            } else {
                // 没有 chat:，在 functions 节点末尾创建 chat 和 backends
                int functionsEndPos = findFunctionsSectionEnd(lines, functionsLine);
                appendLines(sb, lines, 0, functionsEndPos);
                sb.append(System.lineSeparator());
                sb.append(YAML_INDENT).append("chat:").append(System.lineSeparator());
                sb.append(YAML_INDENT + YAML_INDENT).append("backends:").append(System.lineSeparator());
                sb.append(backendsFragment);
                appendLines(sb, lines, functionsEndPos, lines.length);
            }
        } else {
            // 没有 functions:，创建完整层级
            appendLines(sb, lines, 0, lines.length);
            sb.append(System.lineSeparator());
            sb.append("functions:").append(System.lineSeparator());
            sb.append(YAML_INDENT).append("chat:").append(System.lineSeparator());
            sb.append(YAML_INDENT + YAML_INDENT).append("backends:").append(System.lineSeparator());
            sb.append(backendsFragment);
        }
        return sb.toString().trim() + System.lineSeparator();
    }

    // backends：存在则替换同名 backend，否则追加
    private static String replaceOrAppendBackends(String[] lines, int backendsLine,
                                                  String backendsFragment,
                                                  List<Map<String, Object>> selectedModels) {
        // 提取要添加的 backend 名称列表
        List<String> newBackendNames = new ArrayList<>();
        for (Map<String, Object> m : selectedModels) {
            String name = m.get("name") == null ? null : m.get("name").toString();
            if (name != null && !name.isEmpty()) {
                newBackendNames.add(name);
            }
        }

        // 解析现有的 backends 列表
        List<BackendItem> existingBackends = parseExistingBackends(lines, backendsLine);

        // 构建新的 backends 内容
        StringBuilder sb = new StringBuilder();
        // 写入 backends: 这一行
        sb.append(lines[backendsLine]).append(System.lineSeparator());

        // 先写入新的 backends 片段
        sb.append(backendsFragment);

        // 然后保留不在新列表中的现有 backends
        boolean hasNewBackends = !backendsFragment.trim().isEmpty();
        for (BackendItem item : existingBackends) {
            if (!newBackendNames.contains(item.backendName)) {
                if (hasNewBackends) {
                    sb.append(System.lineSeparator());
                }
                // 写入原有 backend 项
                for (int i = item.startLine; i < item.endLine; i++) {
                    sb.append(lines[i]);
                    if (i < item.endLine - 1) {
                        sb.append(System.lineSeparator());
                    }
                }
                hasNewBackends = true;
            }
        }

        // 找到 backends 列表的结束位置，写入剩余内容
        int backendsEndPos = findBackendsEndPosition(lines, backendsLine);
        // 写入 backends 之后的内容
        for (int i = backendsEndPos; i < lines.length; i++) {
            sb.append(System.lineSeparator()).append(lines[i]);
        }

        // 替换原有的 backends 部分
        StringBuilder finalSb = new StringBuilder();
        // 写入 backendsLine 之前的内容
        for (int i = 0; i < backendsLine; i++) {
            finalSb.append(lines[i]).append(System.lineSeparator());
        }
        // 写入新的 backends 内容
        finalSb.append(sb);

        return finalSb.toString().trim() + System.lineSeparator();
    }

    /**
     * 解析现有的 backends 列表。
     */
    private static List<BackendItem> parseExistingBackends(String[] lines, int backendsLine) {
        List<BackendItem> items = new ArrayList<>();
        int currentStart = -1;
        String currentBackendName = null;

        for (int i = backendsLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!line.startsWith(YAML_INDENT + YAML_INDENT + YAML_INDENT)) {
                if (currentStart >= 0) {
                    items.add(new BackendItem(currentBackendName, currentStart, i));
                }
                break;
            }

            // 检查是否是新的 backend 项开始（- backend: xxx）
            if (trimmed.startsWith("- backend:")) {
                if (currentStart >= 0) {
                    items.add(new BackendItem(currentBackendName, currentStart, i));
                }
                // 提取 backend 名称
                String[] parts = trimmed.substring(trimmed.indexOf("backend:") + 8).trim().split("\\s+");
                currentBackendName = parts.length > 0 ? parts[0] : null;
                currentStart = i;
            }
        }

        // 处理最后一个
        if (currentStart >= 0) {
            int endPos = lines.length;
            for (int i = currentStart + 1; i < lines.length; i++) {
                String line = lines[i];
                if (!line.startsWith(YAML_INDENT + YAML_INDENT + YAML_INDENT)) {
                    endPos = i;
                    break;
                }
            }
            items.add(new BackendItem(currentBackendName, currentStart, endPos));
        }

        return items;
    }

    // 查找 backends 列表结束位置
    private static int findBackendsEndPosition(String[] lines, int backendsLine) {
        for (int i = backendsLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            // 如果缩进小于等于 backends 的缩进，说明列表结束了
            if (!line.startsWith(YAML_INDENT + YAML_INDENT + YAML_INDENT)) {
                return i;
            }
        }
        return lines.length;
    }

    // 查找 chat 节点结束位置
    private static int findChatSectionEnd(String[] lines, int chatLine) {
        int chatIndentLevel = YAML_INDENT.length(); // 2 空格
        for (int i = chatLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }

            // 计算当前行的缩进级别
            int currentIndent = line.length() - line.trim().length();
            // 如果缩进级别 <= chat 的缩进级别，说明 chat 节点结束
            if (currentIndent <= chatIndentLevel) {
                return i;
            }
        }
        return lines.length;
    }

    // 查找 functions 节点结束位置
    private static int findFunctionsSectionEnd(String[] lines, int functionsLine) {
        for (int i = functionsLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }

            // 如果是顶级节点，说明 functions 节点结束
            if (line.length() == trimmed.length()) {
                return i;
            }
        }
        return lines.length;
    }

    // backends 列表的一个 item
    private static class BackendItem {
        final String backendName;
        final int startLine;
        final int endLine;

        BackendItem(String backendName, int startLine, int endLine) {
            this.backendName = backendName;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    // 查找 models行位置
    private static int findModelsStartLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if ("models:".equals(lines[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    // 查找 models 段插入位置（最后一个模型项之后）
    private static int findModelsInsertPosition(String[] lines, int modelsStartLine) {
        int insertPos = lines.length;
        for (int i = modelsStartLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith(YAML_COMMENT_PREFIX)) {
                continue;
            }
            // 如果遇到非缩进行（顶级节点），说明 models 部分结束了
            if (!line.startsWith(YAML_INDENT)) {
                insertPos = i;
                break;
            }
        }
        return insertPos;
    }

    // 追加行到 StringBuilder
    private static void appendLines(StringBuilder sb, String[] lines, int start, int end) {
        for (int i = start; i < end; i++) {
            sb.append(lines[i]);
            if (i < end - 1) {
                sb.append(System.lineSeparator());
            }
        }
    }


    private static YAMLMapper createYamlMapper() {
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory factory =
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                        .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        return new YAMLMapper(factory);
    }

    // 在 model.yml 的 models 列表中，找与某 provider+model 最匹配的定义块
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findMatchingModelDef(List<Map<String, Object>> allModelDefs,
                                                            String providerName,
                                                            JsonNode modelNode) {
        String providerLower = providerName == null ? "" : providerName.toLowerCase();
        String modelId = getNodeTextValue(modelNode, "id");
        String modelName = getNodeTextValue(modelNode, "name");
        String modelIdLower = modelId == null ? "" : modelId.toLowerCase();
        String modelNameLower = modelName == null ? "" : modelName.toLowerCase();

        //  provider 映射
        String aliasName = null;
        if ("moonshot".equalsIgnoreCase(providerName)) {
            aliasName = "kimi";
        } else if ("zai".equalsIgnoreCase(providerName)) {
            aliasName = "chatglm";
        } else if ("qwen-portal".equalsIgnoreCase(providerName)) {
            aliasName = "qwen";
        }
        String aliasLower = aliasName == null ? "" : aliasName.toLowerCase();

        for (Map<String, Object> def : allModelDefs) {
            if (def == null) continue;
            String name = def.get("name") == null ? "" : def.get("name").toString();
            String type = def.get("type") == null ? "" : def.get("type").toString();
            Object modelFieldObj = def.get("model");
            String modelField = modelFieldObj == null ? "" : modelFieldObj.toString();

            String nameLower = name.toLowerCase();
            String typeLower = type.toLowerCase();
            String modelFieldLower = modelField.toLowerCase();

            if (!nameLower.isEmpty() && providerLower.contains(nameLower)) {
                return def;
            }
            if (!aliasLower.isEmpty() && nameLower.equals(aliasLower)) {
                return def;
            }

            // provider 与 type 近似
            if (!typeLower.isEmpty() &&
                    (providerLower.contains(typeLower) || typeLower.contains(providerLower))) {
                return def;
            }

            // models.json 的 id/name 在 model.yml 的 model 字段中出现
            if (!modelIdLower.isEmpty() && modelFieldLower.contains(modelIdLower)) {
                return def;
            }
            if (!modelNameLower.isEmpty() && modelFieldLower.contains(modelNameLower)) {
                return def;
            }
        }
        return null;
    }

    // 从 JsonNode 安全取字符串字段
    private static String getNodeTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().trim().isEmpty()) {
            return null;
        }
        return fieldNode.asText().trim();
    }

    // 追加到 models / backends
    private static class OpenClawFragments {
        final String modelsFragment;    // 要追加到 models: 下的内容
        final String backendsFragment;  // 要追加到 functions.chat.backends 下的内容

        OpenClawFragments(String modelsFragment, String backendsFragment) {
            this.modelsFragment = modelsFragment;
            this.backendsFragment = backendsFragment;
        }

        boolean isEmpty() {
            return (modelsFragment == null || modelsFragment.trim().isEmpty()) &&
                    (backendsFragment == null || backendsFragment.trim().isEmpty());
        }
    }

    private static class OpenClawFragmentsResult {
        final OpenClawFragments fragments;
        final List<Map<String, Object>> selectedModels;

        OpenClawFragmentsResult(OpenClawFragments fragments, List<Map<String, Object>> selectedModels) {
            this.fragments = fragments;
            this.selectedModels = selectedModels;
        }
    }
}

