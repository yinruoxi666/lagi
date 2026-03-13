package ai.utils;

import ai.config.ContextLoader;

public class ExpressionParser {
    /**
     * SPEL ExpressionParse
     * @param expression like "${app.name:default}"
     * @return value
     */
    public static String parse(String expression) {
        if (!expression.startsWith("${") || !expression.endsWith("}")) {
            throw new IllegalArgumentException("expression：" + expression + "，format：${key:default}");
        }

        String content = expression.substring(2, expression.length() - 1);
        String key;
        String defaultValue = null;

        int colonIndex = content.indexOf(":");
        if (colonIndex > 0) {
            key = content.substring(0, colonIndex).trim();
            defaultValue = content.substring(colonIndex + 1).trim();
        } else {
            key = content.trim();
        }

        String configValue = ContextLoader.getProperties(key);
        if (configValue != null && !configValue.isEmpty()) {
            return configValue;
        }

        if (defaultValue == null) {
            throw new RuntimeException("properties: " + key + "not exist");
        }
        return defaultValue;
    }



}
