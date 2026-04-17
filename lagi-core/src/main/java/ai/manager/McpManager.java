package ai.manager;

import ai.common.pojo.McpBackend;
import ai.config.pojo.McpConfig;
import ai.mcps.CommonSseMcpClient;
import ai.mcps.SyncMcpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class McpManager {
    @Getter
    private static McpManager instance = new McpManager();
    private McpManager(){}
    private final Map<String, McpBackend> mcpBackendsMap = new ConcurrentHashMap<>();


    public void register(McpConfig config)
    {
        mcpBackendsMap.clear();
        if (config == null || Boolean.TRUE.equals(config.getEnable())) {
            return;
        }
        String defaultDriver = config.getDriver();
        if (defaultDriver != null) {
            try {
                Class.forName(defaultDriver);
                List<McpBackend> server = config.getServers();
                if(server != null) {
                    for (McpBackend mcpBackend : server) {
                        if(mcpBackend.getDriver() == null) {
                            mcpBackend.setDriver(defaultDriver);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {

            }
        }
        register(config.getServers());
    }

    public void register(List<McpBackend> mcpBackends)
    {
        if(mcpBackends == null) {
            return;
        }
        for (McpBackend mcpBackend : mcpBackends) {
            register(mcpBackend);
        }
    }

    public void register(McpBackend mcpBackend)
    {
        if (mcpBackend == null || mcpBackend.getName() == null) {
            return;
        }
        String name = mcpBackend.getName();
        McpBackend previous = mcpBackendsMap.put(name, mcpBackend);
        if (previous != null) {
            log.debug("mcpBackend ({}) replaced (e.g. config reload)", name);
        }
    }

    public SyncMcpClient getNewMcpClient(String name)
    {
        McpBackend mcpBackend = mcpBackendsMap.get(name);
        if(mcpBackend == null) {
            return null;
        }
        String driver = mcpBackend.getDriver();
        if(driver == null) {
            return new CommonSseMcpClient(mcpBackend);
        }
        try {
            Class<?> aClass = Class.forName(driver);
            Constructor<?> constructor = aClass.getConstructor(McpBackend.class);
            return (SyncMcpClient) constructor.newInstance(mcpBackend);
        } catch (Exception e) {
            log.error("get mcp driver: {}  error", driver);
        }
        return new CommonSseMcpClient(mcpBackend);
    }

    public List<McpBackend> getMcpBackends() {
        return new ArrayList<>(mcpBackendsMap.values()).stream()
                .sorted(Comparator.comparing(McpBackend::getPriority))
                .collect(Collectors.toList());
    }

}
