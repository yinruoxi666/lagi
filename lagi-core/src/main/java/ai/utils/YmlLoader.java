package ai.utils;

import ai.config.ContextLoader;
import cn.hutool.core.convert.Convert;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class YmlLoader {
    public static   <T> T loaderProperties(String yamlName, String fieldName, Class<T> clazz) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream inputStream = ContextLoader.class.getResourceAsStream("/" + yamlName);){
            TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
            };
            Map<String, Object> map = mapper.readValue(inputStream, mapType);
            return  Convert.convert(clazz, map.get(fieldName));
        } catch (IOException e) {
            log.error("加载配置文件失败：{}", e.getMessage(), e);
        }
        return null;
    }

    public static  <T> T loaderProperties(String yamlName, String fieldName, cn.hutool.core.lang.TypeReference<T> typeReference) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream inputStream = ContextLoader.class.getResourceAsStream("/" + yamlName);){
            TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
            };
            Map<String, Object> map = mapper.readValue(inputStream, mapType);
            return  Convert.convert(typeReference, map.get(fieldName));
        } catch (IOException e) {
            log.error("加载配置文件失败：{}", e.getMessage(), e);
        }
        return null;
    }


    public static void flattenYamlToProperties(Object obj, String parentKey, Properties props) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String currentKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();
                flattenYamlToProperties(entry.getValue(), currentKey, props);
            }
        }
        else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            props.setProperty(parentKey+".size", ""+ list.size());
            for (int i = 0; i < list.size(); i++) {
                String currentKey = parentKey + "[" + i + "]";
                flattenYamlToProperties(list.get(i), currentKey, props);
            }
        }
        else if (obj != null) {
            props.setProperty(parentKey, obj.toString());
        }
    }


    public static Map<String, Object> readYamlAsMap(String yamlPath) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream inputStream = openYamlInputStream(yamlPath);){
            TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
            };
            return mapper.readValue(inputStream, mapType);
        } catch (IOException e) {
            log.error("读取yaml失败，path：{}", yamlPath, e);
        }
        return null;
    }

    private static InputStream openYamlInputStream(String yamlPath) throws IOException {
        if (yamlPath == null || yamlPath.trim().isEmpty()) {
            throw new IOException("yamlPath不能为空");
        }

        String path = yamlPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return new URL(path).openStream();
        }

        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            return Files.newInputStream(filePath);
        }

        String classpathPath = path.startsWith("/") ? path.substring(1) : path;
        InputStream classpathStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath);
        if (classpathStream != null) {
            return classpathStream;
        }

        throw new IOException("未找到yaml资源: " + yamlPath);
    }

    public static <T> T loadYaml(String yamlPath, Class<T> clazz) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream inputStream = openYamlInputStream(yamlPath)) {
            return mapper.readValue(inputStream, clazz);
        } catch (IOException e) {
            log.error("加载yaml失败，path：{}", yamlPath, e);
        }
        return null;
    }

    public static void writeYaml(String yamlPath, Object obj) {
        YAMLMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        try (OutputStream outputStream = openYamlOutputStream(yamlPath)) {
            mapper.writeValue(outputStream, obj);
        } catch (IOException e) {
            log.error("写入yaml失败，path：{}", yamlPath, e);
        }
    }

    private static OutputStream openYamlOutputStream(String yamlPath) throws IOException {
        if (yamlPath == null || yamlPath.trim().isEmpty()) {
            throw new IOException("yamlPath不能为空");
        }

        String path = yamlPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            throw new IOException("不支持写入http/https地址: " + yamlPath);
        }

        Path filePath;
        if (path.startsWith("file:")) {
            filePath = Paths.get(URI.create(path));
        } else {
            filePath = Paths.get(path);
        }

        Path parent = filePath.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        return Files.newOutputStream(filePath);
    }

    private static YAMLMapper createYamlMapper() {
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        return new YAMLMapper(yamlFactory);
    }

    public static Map<String, Object> loadYamlAsMap(String yamlPath) {
        ObjectMapper mapper = createYamlMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream inputStream = openYamlInputStream(yamlPath);){
            TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
            };
            return mapper.readValue(inputStream, mapType);
        } catch (IOException e) {
            log.error("读取yaml失败，path：{}", yamlPath, e);
        }
        return null;
    }

}
