package ai.workflow.utils;

import ai.utils.ResourceUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 节点元数据管理器
 * 
 * 负责管理和提供节点的元数据信息，支持节点的动态扩展
 * 当新增节点时，只需在 prompts/nodes 目录添加节点定义文件，无需修改代码
 * 
 * 功能：
 * 1. 加载节点定义
 * 2. 提供节点描述
 * 3. 提供节点分类
 * 4. 生成节点选择提示
 */
@Slf4j
public class NodeMetadataManager {
    
    private static final Map<String, NodeMetadata> nodeMetadataMap = new HashMap<>();
    private static boolean initialized = false;
    
    /**
     * 节点元数据
     */
    @Data
    public static class NodeMetadata {
        private String type;              // 节点类型
        private String name;              // 节点名称
        private String description;       // 节点描述
        private String category;          // 节点分类
        private List<String> inputFields; // 输入字段
        private List<String> outputFields;// 输出字段
        private String jsonExample;       // JSON 示例
        private int priority;             // 优先级（用于推荐排序）
        
        public NodeMetadata(String type, String name, String category) {
            this.type = type;
            this.name = name;
            this.category = category;
            this.inputFields = new ArrayList<>();
            this.outputFields = new ArrayList<>();
            this.priority = 50; // 默认优先级
        }
    }
    
    /**
     * 节点分类
     */
    public enum NodeCategory {
        CONTROL_FLOW("控制流", "start, end, condition, loop, parallel, block-start, block-end"),
        LLM("AI能力", "llm, agent, mcp-agent, knowledge-base, intent-recognition"),
        DATA("数据处理", "database-query, database-update, api, program, code-logic"),
        MEDIA("多媒体", "image2text, image2detect, image2enhance, image2video, text2image, text2video, video2enhance, video2track, ocr, asr, tts, translate"),
        CONTENT("内容安全", "sensitive"),
        UTILITY("工具", "comment");
        
        private final String displayName;
        private final String nodeTypes;
        
        NodeCategory(String displayName, String nodeTypes) {
            this.displayName = displayName;
            this.nodeTypes = nodeTypes;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public List<String> getNodeTypes() {
            return Arrays.asList(nodeTypes.split(",\\s*"));
        }
    }
    
    /**
     * 初始化节点元数据
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        log.info("Initializing node metadata manager");
        
        try {
            // 从 prompts/nodes 目录加载节点定义
            Map<String, String> nodeFiles = ResourceUtil.loadMultipleFromDirectory(
                "/prompts/nodes", "node_", ".md"
            );
            
            for (Map.Entry<String, String> entry : nodeFiles.entrySet()) {
                String nodeType = entry.getKey();
                String content = entry.getValue();
                
                NodeMetadata metadata = parseNodeDefinition(nodeType, content);
                if (metadata != null) {
                    nodeMetadataMap.put(nodeType, metadata);
                }
            }
            
            log.info("Loaded {} node metadata definitions", nodeMetadataMap.size());
            initialized = true;
            
        } catch (Exception e) {
            log.error("Failed to initialize node metadata: {}", e.getMessage(), e);
            // 即使失败也标记为已初始化，避免重复尝试
            initialized = true;
        }
    }
    
    /**
     * 解析节点定义文件
     */
    private static NodeMetadata parseNodeDefinition(String nodeType, String content) {
        try {
            // 分割描述和 JSON 示例
            String[] parts = content.split("={5,100}");
            if (parts.length < 2) {
                log.warn("Invalid node definition format for type: {}", nodeType);
                return null;
            }
            
            String description = parts[0].trim();
            String jsonExample = parts[1].trim();
            
            // 提取节点名称（从描述第一行）
            String[] lines = description.split("\n");
            String name = lines.length > 0 ? lines[0].replaceAll("^#+\\s*", "").trim() : nodeType;
            
            // 确定分类
            String category = determineCategory(nodeType);
            
            NodeMetadata metadata = new NodeMetadata(nodeType, name, category);
            metadata.setDescription(description);
            metadata.setJsonExample(jsonExample);
            
            // 从 JSON 示例中提取输入输出字段（简化版）
            // 实际实现可以更复杂，这里只是示例
            extractFieldsFromJson(metadata, jsonExample);
            
            return metadata;
            
        } catch (Exception e) {
            log.error("Failed to parse node definition for {}: {}", nodeType, e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JSON 示例中提取字段信息
     */
    private static void extractFieldsFromJson(NodeMetadata metadata, String jsonExample) {
        // 简化实现：查找常见的输入输出字段
        if (jsonExample.contains("\"inputs\"")) {
            // 尝试提取输入字段名
            String[] commonInputs = {"model", "prompt", "query", "url", "text", "category", 
                                    "databaseName", "sql", "method", "body", "headers"};
            for (String field : commonInputs) {
                if (jsonExample.contains("\"" + field + "\"")) {
                    metadata.getInputFields().add(field);
                }
            }
        }
        
        if (jsonExample.contains("\"outputs\"")) {
            // 尝试提取输出字段名
            String[] commonOutputs = {"result", "intent", "statusCode", "body"};
            for (String field : commonOutputs) {
                if (jsonExample.contains("\"" + field + "\"")) {
                    metadata.getOutputFields().add(field);
                }
            }
        }
    }
    
    /**
     * 确定节点分类
     */
    private static String determineCategory(String nodeType) {
        for (NodeCategory category : NodeCategory.values()) {
            if (category.getNodeTypes().contains(nodeType)) {
                return category.getDisplayName();
            }
        }
        return "其他";
    }
    
    /**
     * 获取节点元数据
     */
    public static NodeMetadata getNodeMetadata(String nodeType) {
        if (!initialized) {
            initialize();
        }
        return nodeMetadataMap.get(nodeType);
    }
    
    /**
     * 获取所有节点元数据
     */
    public static Map<String, NodeMetadata> getAllNodeMetadata() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(nodeMetadataMap);
    }
    
    /**
     * 获取指定分类的节点
     */
    public static List<NodeMetadata> getNodesByCategory(String category) {
        if (!initialized) {
            initialize();
        }
        
        return nodeMetadataMap.values().stream()
            .filter(node -> category.equals(node.getCategory()))
            .collect(Collectors.toList());
    }
    
    /**
     * 生成节点选择提示（按分类组织）
     * 用于提示 LLM 选择合适的节点
     */
    public static String generateNodeSelectionPrompt(List<String> excludeNodes) {
        if (!initialized) {
            initialize();
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 可用节点类型\n\n");
        prompt.append("以下是按功能分类的所有可用节点，请根据任务需求选择合适的节点：\n\n");
        
        // 按分类组织节点
        for (NodeCategory category : NodeCategory.values()) {
            List<NodeMetadata> categoryNodes = getNodesByCategory(category.getDisplayName())
                .stream()
                .filter(node -> !excludeNodes.contains(node.getType()))
                .collect(Collectors.toList());
            
            if (categoryNodes.isEmpty()) {
                continue;
            }
            
            prompt.append("### ").append(category.getDisplayName()).append("\n\n");
            
            for (NodeMetadata node : categoryNodes) {
                prompt.append("**").append(node.getName()).append("** (`").append(node.getType()).append("`)\n");
                
                // 提取描述的第一段
                String[] descLines = node.getDescription().split("\n");
                for (String line : descLines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        prompt.append("- ").append(line).append("\n");
                        break;
                    }
                }
                
                // 输入输出信息
                if (!node.getInputFields().isEmpty()) {
                    prompt.append("- 输入: ").append(String.join(", ", node.getInputFields())).append("\n");
                }
                if (!node.getOutputFields().isEmpty()) {
                    prompt.append("- 输出: ").append(String.join(", ", node.getOutputFields())).append("\n");
                }
                
                prompt.append("\n");
            }
        }
        
        return prompt.toString();
    }
    
    /**
     * 生成节点详细配置提示
     * 用于指导 LLM 正确配置节点
     */
    public static String generateNodeConfigurationPrompt(List<String> nodeTypes, List<String> excludeNodes) {
        if (!initialized) {
            initialize();
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 节点配置参考\n\n");
        
        for (String nodeType : nodeTypes) {
            if (excludeNodes.contains(nodeType)) {
                continue;
            }
            
            NodeMetadata metadata = nodeMetadataMap.get(nodeType);
            if (metadata == null) {
                continue;
            }
            
            prompt.append("### ").append(metadata.getName()).append(" (`").append(nodeType).append("`)\n\n");
            prompt.append(metadata.getDescription()).append("\n\n");
            
            if (metadata.getJsonExample() != null) {
                prompt.append("**配置示例：**\n\n");
                prompt.append("```json\n");
                prompt.append(metadata.getJsonExample());
                prompt.append("\n```\n\n");
            }
        }
        
        return prompt.toString();
    }
    
    /**
     * 获取节点统计信息
     */
    public static Map<String, Object> getStatistics() {
        if (!initialized) {
            initialize();
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", nodeMetadataMap.size());
        
        Map<String, Long> categoryCount = nodeMetadataMap.values().stream()
            .collect(Collectors.groupingBy(NodeMetadata::getCategory, Collectors.counting()));
        stats.put("categoryCounts", categoryCount);
        
        return stats;
    }
    
    /**
     * 重新加载节点元数据（用于开发测试）
     */
    public static synchronized void reload() {
        log.info("Reloading node metadata");
        nodeMetadataMap.clear();
        initialized = false;
        initialize();
    }
}

