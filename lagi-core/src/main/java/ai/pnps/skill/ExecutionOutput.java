package ai.pnps.skill;

import lombok.ToString;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 执行结果（对齐 ms-agent {@code ExecutionOutput}）。
 */
@ToString
public class ExecutionOutput {

    private Object returnValue;
    private String stdout = "";
    private String stderr = "";
    private int exitCode;
    private final Map<String, Path> outputFiles = new LinkedHashMap<>();
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
    private double durationMs;

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout != null ? stdout : "";
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr != null ? stderr : "";
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public Map<String, Path> getOutputFiles() {
        return Collections.unmodifiableMap(outputFiles);
    }

    public Map<String, Path> mutableOutputFiles() {
        return outputFiles;
    }

    public Map<String, Object> getArtifacts() {
        return Collections.unmodifiableMap(artifacts);
    }

    public Map<String, Object> mutableArtifacts() {
        return artifacts;
    }

    public double getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(double durationMs) {
        this.durationMs = durationMs;
    }
}
