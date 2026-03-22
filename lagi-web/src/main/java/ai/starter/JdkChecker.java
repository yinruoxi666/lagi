package ai.starter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Slf4j
public class JdkChecker {
    private static final int MIN_JAVA_VERSION = 8;

    public static JdkCheckResult check() {
        JdkCheckResult result = new JdkCheckResult();

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.trim().isEmpty()) {
            javaHome = System.getProperty("java.home");
        }
        result.setJavaHome(javaHome);

        try {
            Process process = new ProcessBuilder("java", "-version")
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset())
            );

            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                if (line.contains("version")) {
                    result.setVersion(parseVersion(line));
                }
            }

            process.waitFor();
            result.setInstalled(true);
            result.setRawOutput(output.toString());

        } catch (Exception e) {
            result.setInstalled(false);
            result.setErrorMessage(e.getMessage());
        }

        if (result.isInstalled() && result.getVersion() > 0) {
            result.setVersionOk(result.getVersion() >= MIN_JAVA_VERSION);
        }

        return result;
    }


    private static int parseVersion(String versionLine) {
        try {
            int start = versionLine.indexOf('"');
            int end = versionLine.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                String version = versionLine.substring(start + 1, end);

                if (version.startsWith("1.")) {
                    return 8; // Java 8 显示为 1.8
                }

                String[] parts = version.split("\\.");
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            log.debug("Failed to parse Java version: {}", versionLine);
        }
        return -1;
    }


    public static void printCheckResult(JdkCheckResult result) {

        if (!result.isInstalled()) {
            log.error("No valid Java runtime environment detected. Please install JDK from: https://adoptium.net/temurin/releases/");
            return;
        }

        log.info("Java version does not meet the requirement!");
        log.info("  Java Version: " + (result.getVersion() > 0 ? result.getVersion() : "Unknown"));
        log.info("  JAVA_HOME: " + (result.getJavaHome() != null ? result.getJavaHome() : "Not set"));

        if (!result.isVersionOk()) {
            log.error("Java version does not meet the requirement!");
            log.warn("  Required: Java " + MIN_JAVA_VERSION + " or higher");
            log.warn("  Current: Java " + result.getVersion());
        } else {
            log.info("Java version meets the requirement");
        }

    }


    @Data
    public static class JdkCheckResult {
        private boolean installed = false;
        private int version = -1;
        private boolean versionOk = false;
        private String javaHome;
        private String rawOutput;
        private String errorMessage;
    }
}
