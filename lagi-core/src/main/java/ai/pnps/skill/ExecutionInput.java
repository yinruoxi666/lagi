package ai.pnps.skill;

import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 执行入参（对齐 ms-agent {@code ExecutionInput}）。
 */
@Data
public class ExecutionInput {

    private List<Object> args;
    private  Map<String, Object> kwargs;
    private  Map<String, String> envVars;
    private  Map<String, Object> inputFiles;
    private  String stdin;
    private  Path workingDir;
    private  List<String> requirements;

    public ExecutionInput() {
        this.args = new ArrayList<>();
        this.kwargs = new HashMap<>();
        this.envVars = new LinkedHashMap<>();
        this.inputFiles = new LinkedHashMap<>();
        this.stdin = null;
        this.workingDir = null;
        this.requirements = new ArrayList<>();
    }

    public ExecutionInput(
            List<Object> args,
            Map<String, Object> kwargs,
            Map<String, String> envVars,
            Map<String, Object> inputFiles,
            String stdin,
            Path workingDir,
            List<String> requirements) {
        this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
        this.kwargs = kwargs != null ? new HashMap<>(kwargs) : new HashMap<>();
        this.envVars = envVars != null ? new LinkedHashMap<>(envVars) : new LinkedHashMap<>();
        this.inputFiles = inputFiles != null ? new LinkedHashMap<>(inputFiles) : new LinkedHashMap<>();
        this.stdin = stdin;
        this.workingDir = workingDir;
        this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
    }

//    public List<Object> getArgs() {
//        return Collections.unmodifiableList(args);
//    }
//
//    public Map<String, Object> getKwargs() {
//        return Collections.unmodifiableMap(kwargs);
//    }
//
//    public Map<String, String> getEnvVars() {
//        return envVars;
//    }
//
//    public Map<String, Object> getInputFiles() {
//        return Collections.unmodifiableMap(inputFiles);
//    }
//
//    public String getStdin() {
//        return stdin;
//    }
//
//    public Path getWorkingDir() {
//        return workingDir;
//    }
//
//    public List<String> getRequirements() {
//        return Collections.unmodifiableList(requirements);
//    }

    /** 可变的 env 视图，用于追加 {@code UPSTREAM_OUTPUTS} 等。 */
    public Map<String, String> mutableEnvVars() {
        return envVars;
    }

    public ExecutionInput copy() {
        return new ExecutionInput(
                new ArrayList<>(args),
                new HashMap<>(kwargs),
                new LinkedHashMap<>(envVars),
                new LinkedHashMap<>(inputFiles),
                stdin,
                workingDir,
                new ArrayList<>(requirements));
    }
}
