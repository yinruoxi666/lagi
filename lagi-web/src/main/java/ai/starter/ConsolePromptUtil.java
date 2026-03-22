package ai.starter;


import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Slf4j
public class ConsolePromptUtil {

    public static boolean askYesNo(String message, boolean defaultYes) {

        if (!isInteractive()) {
            log.info("[Auto-selected] {} -> {}\", message, defaultYes ? \"Yes\" : \"No");
            return defaultYes;
        }

        String prompt = defaultYes ? " [Y/n]: " : " [y/N]: ";
        System.out.print(message + prompt);

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, Charset.defaultCharset())
            );
            String input = reader.readLine();

            // Use default value if user presses Enter directly (empty input)
            if (input == null || input.trim().isEmpty()) {
                return defaultYes;
            }

            String trimmed = input.trim().toLowerCase();
            // Support "y" or "yes" for positive confirmation (matches English prompt)
            return trimmed.startsWith("y") || trimmed.startsWith("yes");

        } catch (IOException e) {
            log.warn("Failed to read user input, using default value: {}", defaultYes);
            return defaultYes;
        }
    }

    private static boolean isInteractive() {
        try {
            return System.console() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
