package ai.pnps.skills.filesystem;


import java.util.Map;


public interface Filesystem {
    String read(Map<String, String> arguments);
    String write(Map<String , String> arguments);
    String edit(Map<String , String> arguments);
    String exec(Map<String, String> arguments, long timeoutSeconds);
}
