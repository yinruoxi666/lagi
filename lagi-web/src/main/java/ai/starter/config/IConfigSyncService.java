package ai.starter.config;

public interface IConfigSyncService {

    boolean check();
    /**
     * Export the config to the given path
     */
    void export(String path);

    /**
     * Load the config
     */
    void load();

    String name();

}
