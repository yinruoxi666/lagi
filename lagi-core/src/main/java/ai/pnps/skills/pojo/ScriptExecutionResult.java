package ai.pnps.skills.pojo;

import lombok.ToString;

/**
 * Result of running a skill command (OpenClaw-style: stdout/stderr separated).
 */
@ToString
public final class ScriptExecutionResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timeout;
    private final boolean noOutputTimeout;

    public ScriptExecutionResult(int exitCode, String stdout, String stderr, boolean timeout, boolean noOutputTimeout) {
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timeout = timeout;
        this.noOutputTimeout = noOutputTimeout;
    }

    /**
     * Legacy constructor (single combined stream, e.g. read-tool pseudo results).
     */
    public ScriptExecutionResult(int exitCode, String combinedOutput, boolean timeout) {
        this(exitCode, combinedOutput == null ? "" : combinedOutput, "", timeout, false);
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * Combined view for prompts and backward-compatible assertions.
     */
    public String getOutput() {
        return trim(formatCombined());
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public boolean isNoOutputTimeout() {
        return noOutputTimeout;
    }

    private String formatCombined() {
        if (stderr.isEmpty()) {
            return stdout;
        }
        if (stdout.isEmpty()) {
            return "[stderr]\n" + stderr;
        }
        return "[stdout]\n" + stdout + "\n[stderr]\n" + stderr;
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 20_000) {
            return text;
        }
        return text.substring(text.length() - 20_000);
    }
}
