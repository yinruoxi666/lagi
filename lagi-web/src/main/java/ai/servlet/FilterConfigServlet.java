package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Post;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class FilterConfigServlet extends RestfulServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FilterConfigServlet.class);
    private String lagiYmlPath = null;

    private String getLagiYmlPath() {
        if (lagiYmlPath == null) {
            String userDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                userDir + "/lagi-web/src/main/resources/lagi.yml",
                userDir + "/src/main/resources/lagi.yml",
                "../lagi-web/src/main/resources/lagi.yml",
                userDir + "/WEB-INF/classes/lagi.yml",
                "lagi.yml"
            };
            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    lagiYmlPath = path;
                    break;
                }
            }
            if (lagiYmlPath == null) {
                InputStream resourceStream = FilterConfigServlet.class.getResourceAsStream("/lagi.yml");
                if (resourceStream != null) {
                    try {
                        String tempPath = System.getProperty("java.io.tmpdir") + "/lagi.yml";
                        java.nio.file.Files.copy(resourceStream, Paths.get(tempPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        resourceStream.close();
                        File tempFile = new File(tempPath);
                        if (tempFile.exists()) {
                            lagiYmlPath = tempPath;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return lagiYmlPath;
    }

    @Get("list")
    public List<FilterConfig> list() {
        try {
            List<FilterConfig> dbFilters = FilterConfigService.list();
            if (dbFilters.isEmpty()) {
                syncFromYamlToDatabase();
                dbFilters = FilterConfigService.list();
            }
            return dbFilters;
        } catch (Exception e) {
            log.error("获取过滤器配置列表失败", e);
            return new ArrayList<>();
        }
    }

    private synchronized void syncFromYamlToDatabase() {
        try {
            List<FilterConfig> yamlFilters = loadFromYaml();
            if (yamlFilters != null && !yamlFilters.isEmpty()) {
                for (FilterConfig config : yamlFilters) {
                    try {
                        if (!FilterConfigService.filterConfigCache.containsKey(config.getName())) {
                            FilterConfigService.add(config);
                        }
                    } catch (Exception e) {
                        log.warn("同步过滤器配置到数据库失败: {}", config.getName(), e);
                    }
                }
                log.info("从 YAML 同步了 {} 条过滤器配置到数据库", yamlFilters.size());
            }
        } catch (Exception e) {
            log.error("从 YAML 同步到数据库失败", e);
        }
    }

    private List<FilterConfig> loadFromYaml() {
        try {
            String ymlPath = getLagiYmlPath();
            if (ymlPath == null) {
                return new ArrayList<>();
            }
            
            String encoding = detectEncoding(ymlPath);
            if (encoding == null) {
                encoding = "UTF-8";
            }
            
            ObjectMapper mapper = new YAMLMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            
            InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(ymlPath)),
                java.nio.charset.Charset.forName(encoding)
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = mapper.readValue(reader, Map.class);
            reader.close();
            
            Object filtersObj = yamlMap.get("filters");
            if (filtersObj == null) {
                return new ArrayList<>();
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filtersList = (List<Map<String, Object>>) filtersObj;
            List<FilterConfig> filterConfigs = new ArrayList<>();
            
            for (Map<String, Object> filterMap : filtersList) {
                FilterConfig config = new FilterConfig();
                config.setName((String) filterMap.get("name"));
                
                Object groupsObj = filterMap.get("groups");
                if (groupsObj != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> groupsList = (List<Map<String, Object>>) groupsObj;
                    List<FilterRule> groups = new ArrayList<>();
                    for (Map<String, Object> groupMap : groupsList) {
                        FilterRule rule = new FilterRule();
                        rule.setLevel((String) groupMap.get("level"));
                        rule.setRules((String) groupMap.get("rules"));
                        groups.add(rule);
                    }
                    config.setGroups(groups);
                }
                
                config.setRules((String) filterMap.get("rules"));
                filterConfigs.add(config);
            }
            
            return filterConfigs;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Post("add")
    public Map<String, Object> add(@Body FilterConfig filterConfig) {
        try {
            boolean existed = FilterConfigService.filterConfigCache.containsKey(filterConfig.getName());
            FilterConfigService.add(filterConfig);
            syncToYaml();
            try {
                reloadConfiguration();
            } catch (Exception reloadEx) {
                log.warn("配置重新加载失败，但数据库已保存: {}", reloadEx.getMessage());
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            // 如果已存在，返回更新成功消息，否则返回添加成功消息
            result.put("message", existed ? "更新成功（过滤器已存在，已自动更新）" : "添加成功");
            return result;
        } catch (Exception e) {
            log.error("添加过滤器配置失败", e);
            throw new RuntimeException("添加失败: " + e.getMessage(), e);
        }
    }

    @Post("update")
    public Map<String, Object> update(@Body FilterConfig filterConfig) {
        try {
            FilterConfigService.update(filterConfig);
            syncToYaml();
            try {
                reloadConfiguration();
            } catch (Exception reloadEx) {
                log.warn("配置重新加载失败，但数据库已更新: {}", reloadEx.getMessage());
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "更新成功");
            return result;
        } catch (Exception e) {
            log.error("更新过滤器配置失败", e);
            throw new RuntimeException("更新失败: " + e.getMessage(), e);
        }
    }

    @Post("delete")
    public Map<String, Object> delete(@Body Map<String, String> request) {
        try {
            String name = request.get("name");
            if (name == null || name.trim().isEmpty()) {
                throw new RuntimeException("过滤器名称不能为空");
            }
            
            FilterConfigService.delete(name);
            syncToYaml();
            try {
                reloadConfiguration();
            } catch (Exception reloadEx) {
                log.warn("配置重新加载失败，但数据库已删除: {}", reloadEx.getMessage());
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "删除成功");
            return result;
        } catch (Exception e) {
            log.error("删除过滤器配置失败", e);
            throw new RuntimeException("删除失败: " + e.getMessage(), e);
        }
    }

    private synchronized void syncToYaml() {
        String ymlPath = getLagiYmlPath();
        if (ymlPath == null) {
            log.warn("无法同步到 YAML：找不到 lagi.yml 文件");
            return;
        }
        
        try {
            File ymlFile = new File(ymlPath);
            if (!ymlFile.exists()) {
                log.warn("无法同步到 YAML：文件不存在");
                return;
            }
            
            if (!ymlFile.canWrite()) {
                log.warn("无法同步到 YAML：文件没有写权限");
                return;
            }
            
            Path ymlFilePath = Paths.get(ymlPath);
            Path backupPath = Paths.get(ymlPath + ".backup." + System.currentTimeMillis());
            Files.copy(ymlFilePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            
            String encoding = detectEncoding(ymlPath);
            if (encoding == null) {
                encoding = "UTF-8";
            }
            
            ObjectMapper mapper = new YAMLMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            
            InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(ymlFilePath), 
                java.nio.charset.Charset.forName(encoding)
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = mapper.readValue(reader, Map.class);
            reader.close();
            
            List<FilterConfig> filters = FilterConfigService.list();
            List<Map<String, Object>> filtersList = new ArrayList<>();
            for (FilterConfig config : filters) {
                Map<String, Object> filterMap = new LinkedHashMap<>();
                filterMap.put("name", config.getName());
                
                if (config.getGroups() != null && !config.getGroups().isEmpty()) {
                    List<Map<String, Object>> groupsList = new ArrayList<>();
                    for (FilterRule rule : config.getGroups()) {
                        Map<String, Object> groupMap = new LinkedHashMap<>();
                        groupMap.put("level", rule.getLevel());
                        groupMap.put("rules", rule.getRules());
                        if (rule.getMask() != null) {
                            groupMap.put("mask", rule.getMask());
                        }
                        groupsList.add(groupMap);
                    }
                    filterMap.put("groups", groupsList);
                }
                
                if (config.getRules() != null) {
                    filterMap.put("rules", config.getRules());
                }
                
                if (config.getFilterWindowLength() > 0) {
                    filterMap.put("filter_window_length", config.getFilterWindowLength());
                }
                
                filtersList.add(filterMap);
            }
            
            yamlMap.put("filters", filtersList);
            
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            
            Yaml yaml = new Yaml(options);
            OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(ymlFilePath),
                StandardCharsets.UTF_8
            );
            yaml.dump(yamlMap, writer);
            writer.close();
            
            log.info("成功同步过滤器配置到 YAML 文件，备份已保存到: {}", backupPath);
        } catch (Exception e) {
            log.error("同步到 YAML 文件失败", e);
        }
    }

    private String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            log.warn("检测文件编码失败，使用默认 UTF-8", e);
            return "UTF-8";
        }
    }

    private void reloadConfiguration() {
        try {
            ai.config.ContextLoader.loadContext();
            reloadFilterUtils();
            log.info("配置重新加载成功");
        } catch (Exception e) {
            log.error("配置重新加载失败", e);
            throw new RuntimeException("配置重新加载失败: " + e.getMessage(), e);
        }
    }

    private void reloadFilterUtils() {
        try {
            List<FilterConfig> filters = FilterConfigService.list();
            for (FilterConfig filter : filters) {
                String name = filter.getName();
                try {
                    if ("sensitive".equals(name)) {
                        ai.utils.SensitiveWordUtil.clearRuleMap();
                        // 直接使用从数据库读取的最新配置，而不是从YAML文件读取
                        ai.config.GlobalConfigurations config = ai.config.ContextLoader.configuration;
                        if (config != null) {
                            try {
                                // 更新 filterWindowLength（如果未设置，使用默认值200）
                                java.lang.reflect.Field filterWindowLengthField = 
                                    ai.utils.SensitiveWordUtil.class.getDeclaredField("filterWindowLength");
                                filterWindowLengthField.setAccessible(true);
                                int windowLength = filter.getFilterWindowLength() > 0 ? filter.getFilterWindowLength() : 200;
                                filterWindowLengthField.set(null, windowLength);
                                
                                java.lang.reflect.Method method = config.getClass().getDeclaredMethod("push2wordRule", FilterConfig.class);
                                method.setAccessible(true);
                                method.invoke(config, filter);
                                log.info("成功重新加载敏感词配置: {}, 规则数量: {}, filterWindowLength: {}", 
                                    filter.getName(), 
                                    filter.getGroups() != null ? filter.getGroups().size() : 0,
                                    filter.getFilterWindowLength());
                            } catch (Exception e) {
                                log.error("调用 push2wordRule 失败", e);
                                throw new RuntimeException("重新加载敏感词配置失败: " + e.getMessage(), e);
                            }
                        } else {
                            log.warn("ContextLoader.configuration 为 null，无法重新加载敏感词配置");
                        }
                    } else if ("priority".equals(name)) {
                        List<String> words = convert2List(filter);
                        ai.utils.PriorityWordUtil.reloadWords(words);
                    } else if ("continue".equals(name)) {
                        List<String> words = convert2List(filter);
                        ai.utils.ContinueWordUtil.reloadWords(words);
                    } else if ("stopping".equals(name)) {
                        List<String> words = convert2List(filter);
                        ai.utils.StoppingWordUtil.reloadWords(words);
                    }
                } catch (Exception e) {
                    log.error("重新加载过滤器工具类失败: {}", name, e);
                }
            }
        } catch (Exception e) {
            log.error("重新加载过滤器工具类失败", e);
            throw e;
        }
    }

    private List<String> convert2List(FilterConfig filterItem) {
        String rules = filterItem.getRules();
        if (rules == null || rules.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        String s = rules.replaceAll("\\\\\\\\,", "·regx-dot·");
        List<String> collect = java.util.Arrays.stream(s.split(","))
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .collect(java.util.stream.Collectors.toList());
        collect = collect.stream()
            .map(temp -> temp.replaceAll("·regx-dot·", ","))
            .collect(java.util.stream.Collectors.toList());
        return collect;
    }
}

