package ai.pnps.skill;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Execute skills locally (no Docker) and collect stdout/stderr/output files.
 * <p>
 * This is the Java-side counterpart of ms-agent {@code SkillContainer}.
 */
public class SkillContainer {

    // Keep this minimal; ms-agent's container has more elaborate sandbox logic.
    private static final List<Pattern> LOCAL_DANGEROUS_PATTERNS = new ArrayList<Pattern>() {{
        add(Pattern.compile("os\\.system\\s*\\(", Pattern.CASE_INSENSITIVE));
        add(Pattern.compile("subprocess\\.call\\s*\\([^\\)]*shell\\s*=\\s*True", Pattern.CASE_INSENSITIVE));
        add(Pattern.compile("rm\\s+-rf\\s+/", Pattern.CASE_INSENSITIVE));
        add(Pattern.compile("curl\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE));
        add(Pattern.compile("wget\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE));
    }};

    private final Path workspaceDir;
    private final Path outputDir;
    private final Path logsDir;
    private final long timeoutSeconds;
    private final String pythonExecutable;
    private final Charset charset = StandardCharsets.UTF_8;

    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    public SkillContainer(Path workspaceDir, long timeoutSeconds) {
        this(workspaceDir, timeoutSeconds, "python");
    }

    public SkillContainer(Path workspaceDir, long timeoutSeconds, String pythonExecutable) {
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir").toAbsolutePath().normalize();
        this.outputDir = this.workspaceDir.resolve("outputs");
        this.logsDir = this.workspaceDir.resolve("logs");
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 300;
        this.pythonExecutable = pythonExecutable != null ? pythonExecutable : "python";

        try {
            Files.createDirectories(outputDir);
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to init SkillContainer directories", e);
        }
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Path getLogsDir() {
        return logsDir;
    }

    public ExecutionOutput executePythonScript(Path scriptPath, String skillId, ExecutionInput input) {
        Objects.requireNonNull(scriptPath, "scriptPath");
        ExecutionInput execInput = input != null ? input : new ExecutionInput();

        Path absScript = scriptPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absScript)) {
            ExecutionOutput out = new ExecutionOutput();
            out.setStderr("Python script not found: " + absScript);
            out.setExitCode(-1);
            return out;
        }

        // Security check by reading file content (best-effort).
        try {
            String code = new String(Files.readAllBytes(absScript), charset);
            if (!isSafeCode(code)) {
                ExecutionOutput out = new ExecutionOutput();
                out.setStderr("Security check failed for skill [" + skillId + "]");
                out.setExitCode(-1);
                return out;
            }
        } catch (IOException ignore) {
            // If we can't read, just execute; the skill directory is local.
        }

        long start = System.nanoTime();
        ExecutionOutput executionOutput = new ExecutionOutput();
        Process process = null;
        try {
            Path skillRoot = findSkillRoot(absScript);
            Map<String, String> env = mergeEnv(execInput.getEnvVars());
            env.put("SKILL_OUTPUT_DIR", outputDir.toString());
            env.put("SKILL_LOGS_DIR", logsDir.toString());
            env.put("SKILL_DIR", skillRoot.toString());

            Path workingDir = execInput.getWorkingDir() != null ? execInput.getWorkingDir() : skillRoot;

            List<String> cmd = new ArrayList<String>();
            cmd.add(pythonExecutable);
            cmd.add(absScript.toString());
            cmd.addAll(toArgList(execInput.getArgs()));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir.toFile());
            pb.environment().putAll(env);
            process = pb.start();

            // stdin
            if (execInput.getStdin() != null) {
                writeStdin(process.getOutputStream(), execInput.getStdin());
            } else {
                process.getOutputStream().close();
            }

            Future<String> stdoutFuture = ioExecutor.submit(readStreamTask(process.getInputStream()));
            Future<String> stderrFuture = ioExecutor.submit(readStreamTask(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String stdout = safeGet(stdoutFuture);
            String stderr = safeGet(stderrFuture);

            int exitCode;
            if (!finished) {
                process.destroyForcibly();
                exitCode = -1;
                stderr = (stderr == null ? "" : stderr) + "\nExecution timed out after " + timeoutSeconds + "s";
            } else {
                exitCode = process.exitValue();
            }

            executionOutput.setStdout(stdout);
            executionOutput.setStderr(stderr);
            executionOutput.setExitCode(exitCode);
            executionOutput.mutableOutputFiles().putAll(collectOutputFiles());
            double durMs = (System.nanoTime() - start) / 1_000_000.0;
            executionOutput.setDurationMs(durMs);
            return executionOutput;
        } catch (Exception e) {
            executionOutput.setStderr("Python execution failed: " + e.getMessage());
            executionOutput.setExitCode(-1);
            executionOutput.setDurationMs((System.nanoTime() - start) / 1_000_000.0);
            executionOutput.mutableOutputFiles().putAll(collectOutputFiles());
            return executionOutput;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public ExecutionOutput executePythonCode(String code, String skillId, ExecutionInput input) {
        // Write code to a temp file under logs dir, then run it as a script.
        ExecutionInput execInput = input != null ? input : new ExecutionInput();
        long start = System.nanoTime();
        try {
            Path temp = logsDir.resolve("skill_" + sanitizeSkillId(skillId) + "_"
                    + System.currentTimeMillis() + ".py");
            Files.write(temp, code.getBytes(charset), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ExecutionOutput out = executePythonScript(temp, skillId, execInput);
            out.setDurationMs(out.getDurationMs() + (System.nanoTime() - start) / 1_000_000.0);
            return out;
        } catch (Exception e) {
            ExecutionOutput out = new ExecutionOutput();
            out.setStderr("Python code execution failed: " + e.getMessage());
            out.setExitCode(-1);
            out.setDurationMs((System.nanoTime() - start) / 1_000_000.0);
            return out;
        }
    }

    public ExecutionOutput executeShell(String command, String skillId, ExecutionInput input) {
        ExecutionInput execInput = input != null ? input : new ExecutionInput();
        long start = System.nanoTime();

        Process process = null;
        try {
            Map<String, String> env = mergeEnv(execInput.getEnvVars());
            env.put("SKILL_OUTPUT_DIR", outputDir.toString());
            env.put("SKILL_LOGS_DIR", logsDir.toString());
            // SKILL_DIR can't be inferred reliably without skill root; keep as-is.

            List<String> cmd = buildShellCommand(command);
            Path workingDir = execInput.getWorkingDir() != null ? execInput.getWorkingDir() : workspaceDir;

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir.toFile());
            pb.environment().putAll(env);
            process = pb.start();

            if (execInput.getStdin() != null) {
                writeStdin(process.getOutputStream(), execInput.getStdin());
            } else {
                process.getOutputStream().close();
            }

            Future<String> stdoutFuture = ioExecutor.submit(readStreamTask(process.getInputStream()));
            Future<String> stderrFuture = ioExecutor.submit(readStreamTask(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String stdout = safeGet(stdoutFuture);
            String stderr = safeGet(stderrFuture);

            int exitCode;
            if (!finished) {
                process.destroyForcibly();
                exitCode = -1;
                stderr = (stderr == null ? "" : stderr) + "\nExecution timed out after " + timeoutSeconds + "s";
            } else {
                exitCode = process.exitValue();
            }

            ExecutionOutput executionOutput = new ExecutionOutput();
            executionOutput.setStdout(stdout);
            executionOutput.setStderr(stderr);
            executionOutput.setExitCode(exitCode);
            executionOutput.mutableOutputFiles().putAll(collectOutputFiles());
            executionOutput.setDurationMs((System.nanoTime() - start) / 1_000_000.0);
            return executionOutput;
        } catch (Exception e) {
            ExecutionOutput out = new ExecutionOutput();
            out.setStderr("Shell execution failed: " + e.getMessage());
            out.setExitCode(-1);
            out.setDurationMs((System.nanoTime() - start) / 1_000_000.0);
            out.mutableOutputFiles().putAll(collectOutputFiles());
            return out;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private Map<String, String> mergeEnv(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(envVars);
    }

    private List<String> toArgList(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            list.add(String.valueOf(a));
        }
        return list;
    }

    private Path findSkillRoot(Path scriptPath) {
        Path dir = scriptPath.getParent();
        while (dir != null) {
            if (Files.exists(dir.resolve("SKILL.md"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return scriptPath.getParent();
    }

    private boolean isSafeCode(String code) {
        if (code == null) {
            return true;
        }
        for (Pattern p : LOCAL_DANGEROUS_PATTERNS) {
            if (p.matcher(code).find()) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildShellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            List<String> cmd = new ArrayList<>();
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(command);
            return cmd;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add(command);
        return cmd;
    }

    private Callable<String> readStreamTask(InputStream stream) {
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                return sb.toString();
            }
        };
    }

    private String safeGet(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException e) {
            return "";
        }
    }

    private void writeStdin(OutputStream os, String stdin) throws IOException {
        // Best-effort; assumes UTF-8.
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, charset));
        bw.write(stdin);
        bw.flush();
        bw.close();
    }

    private Map<String, Path> collectOutputFiles() {
        Map<String, Path> files = new LinkedHashMap<>();
        if (!Files.exists(outputDir)) {
            return files;
        }
        try {
            Files.walk(outputDir).filter(Files::isRegularFile).forEach(p -> {
                String rel = outputDir.relativize(p).toString().replace(File.separatorChar, '/');
                files.put(rel, p);
            });
        } catch (IOException ignore) {
        }
        return files;
    }

    private String sanitizeSkillId(String skillId) {
        if (skillId == null) {
            return "unknown";
        }
        return skillId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

