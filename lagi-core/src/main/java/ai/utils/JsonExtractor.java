package ai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonExtractor {
    /**
     * Extracts JSON strings from a given input string.
     * This method looks for content that appears to be valid JSON objects or arrays.
     *
     * @param input The string that may contain JSON content
     * @return A list of extracted JSON strings
     */
    public static List<String> extractJsonStrings(String input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> jsonStrings = new ArrayList<>();
        
        // Use a more robust approach to avoid regex backtracking issues
        try {
            // Simple pattern to find potential JSON objects
            Pattern objectPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");
            Matcher objectMatcher = objectPattern.matcher(input);

            while (objectMatcher.find()) {
                String candidate = objectMatcher.group();
                // Validate that it's actually valid JSON
                if (isValidJson(candidate)) {
                    jsonStrings.add(candidate);
                }
            }
        } catch (Exception e) {
            // If regex fails, fall back to manual parsing
            jsonStrings.addAll(extractJsonManually(input, '{', '}'));
        }

        return jsonStrings;
    }

    public static List<String> extractJsonArrayStrings(String input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> jsonStrings = new ArrayList<>();
        
        // Use a more robust approach to avoid regex backtracking issues
        try {
            // Simple pattern to find potential JSON arrays
            Pattern arrayPattern = Pattern.compile("\\[[^\\[\\]]*(?:\\[[^\\[\\]]*\\][^\\[\\]]*)*\\]");
            Matcher arrayMatcher = arrayPattern.matcher(input);

            while (arrayMatcher.find()) {
                String candidate = arrayMatcher.group();
                // Validate that it's actually valid JSON
                if (isValidJson(candidate)) {
                    jsonStrings.add(candidate);
                }
            }
        } catch (Exception e) {
            // If regex fails, fall back to manual parsing
            jsonStrings.addAll(extractJsonManually(input, '[', ']'));
        }

        return jsonStrings;
    }

    /**
     * Extract a single JSON string from input. Returns the first match.
     *
     * @param input The string that may contain JSON content
     * @return The first found JSON string or null if none is found
     */
    public static String extractFirstJsonString(String input) {
        List<String> results = extractJsonStrings(input);
        return results.isEmpty() ? null : results.get(0);
    }

    public static String extractFirstJsonArray(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        List<String> results = extractJsonArrayStrings(input);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Validate if a string is valid JSON
     */
    private static boolean isValidJson(String jsonString) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Manual JSON extraction using bracket counting to avoid regex issues
     */
    private static List<String> extractJsonManually(String input, char openBracket, char closeBracket) {
        List<String> results = new ArrayList<>();
        int start = -1;
        int bracketCount = 0;
        boolean inString = false;
        char escapeChar = '\\';
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            // Handle string literals to avoid counting brackets inside strings
            if (c == '"' && (i == 0 || input.charAt(i - 1) != escapeChar)) {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == openBracket) {
                    if (start == -1) {
                        start = i;
                    }
                    bracketCount++;
                } else if (c == closeBracket) {
                    bracketCount--;
                    if (bracketCount == 0 && start != -1) {
                        String candidate = input.substring(start, i + 1);
                        if (isValidJson(candidate)) {
                            results.add(candidate);
                        }
                        start = -1;
                    }
                }
            }
        }
        
        return results;
    }

    // Example usage
    public static void main(String[] args) {
        String testString = "Some text before {\"name\": \"John\", \"age\": 30} and some text after. " +
                "Also here's an array [1, 2, 3, 4] and another object {\"city\": \"New York\"}";

        List<String> jsonStrings = extractJsonStrings(testString);
        System.out.println("Found " + jsonStrings.size() + " JSON strings:");
        for (String json : jsonStrings) {
            System.out.println(json);
        }

        List<String> jsonArrayStrings = extractJsonArrayStrings(testString);
        System.out.println("Found " + jsonArrayStrings.size() + " JSON arrays:");
        for (String json : jsonArrayStrings) {
            System.out.println(json);
        }
    }
}