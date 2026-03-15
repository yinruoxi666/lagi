package ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ContextLoader {

    private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

    private static final Properties PROPERTIES = new Properties();

    public static GlobalConfigurations configuration = null;

    public static String getProperties(String key) {
        return PROPERTIES.getProperty(key);
    }

    private static void loadContextByInputStream(InputStream inputStream) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            GlobalConfigurations loadedConfiguration = mapper.readValue(reader, GlobalConfigurations.class);
            loadedConfiguration.init();
            configuration = loadedConfiguration;
        } catch (Exception e) {
            configuration = null;
            log.error("从 InputStream 加载配置失败", e);
            throw new RuntimeException("加载配置失败", e);
        }
    }

    public static void loadContextByResource(String yamlName) {
        InputStream resourceAsStream = ContextLoader.class.getResourceAsStream("/" + yamlName);
        if (resourceAsStream == null) {
            throw new RuntimeException("classpath resource not found: " + yamlName);
        }
        loadProperties(resourceAsStream);
        InputStream resourceAsStream1 = ContextLoader.class.getResourceAsStream("/" + yamlName);
        loadContextByInputStream(resourceAsStream1);
    }

    private static void loadProperties(InputStream resourceAsStream)  {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(resourceAsStream);
            flattenYamlToProperties(yamlMap, "", PROPERTIES);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenYamlToProperties(Object obj, String parentKey, Properties props) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String currentKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();
                flattenYamlToProperties(entry.getValue(), currentKey, props);
            }
        }
        else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                String currentKey = parentKey + "[" + i + "]";
                flattenYamlToProperties(list.get(i), currentKey, props);
            }
        }
        else if (obj != null) {
            props.setProperty(parentKey, obj.toString());
        }
    }

    public static void loadContextByFilePath(String filePath) {
        try {
            loadProperties(Files.newInputStream(Paths.get(filePath)));

            String encoding = detectEncoding(filePath);
            if (encoding == null) {
                encoding = "UTF-8";
            }
            InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(filePath)),
                java.nio.charset.Charset.forName(encoding)
            );
            ObjectMapper mapper = new YAMLMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            try {
                GlobalConfigurations loadedConfiguration = mapper.readValue(reader, GlobalConfigurations.class);
                loadedConfiguration.init();
                configuration = loadedConfiguration;
                reader.close();
            } catch (Exception e) {
                configuration = null;
                reader.close();
                log.error("加载配置文件失败: {}", filePath, e);
                throw new RuntimeException("加载配置文件失败: " + filePath, e);
            }
        } catch (IOException e) {
            log.error("打开配置文件失败: {}", filePath, e);
            throw new RuntimeException("打开配置文件失败: " + filePath, e);
        }
    }

    private static String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            log.warn("检测文件编码失败，使用默认 UTF-8: {}", filePath, e);
            return "UTF-8";
        }
    }

    public static synchronized void loadContext() {
        if (configuration != null) return;

        String configPath = System.getProperty("linkmind.config");
        if (configPath != null && !configPath.trim().isEmpty()) {
            try {
                loadContextByFilePath(configPath.trim());
                if (configuration != null) {
                    return;
                }
            } catch (Exception e) {
                log.error("Failed to load configuration from command line path: {}", configPath, e);
            }
        }

        try {
            if(configuration == null) {
                loadContextByResource("lagi.yml");
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if(configuration == null) {
            try {
                loadContextByFilePath("lagi-web/src/main/resources/lagi.yml");
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        if(configuration == null) {
            try {
                loadContextByFilePath("../lagi-web/src/main/resources/lagi.yml");
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        ContextLoader.loadContext();
        System.out.println(ContextLoader.configuration);;
    }
}
