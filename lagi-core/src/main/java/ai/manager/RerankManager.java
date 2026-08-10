package ai.manager;

import ai.config.ContextLoader;
import ai.config.pojo.RerankConfig;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.impl.LocalRerankAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RerankManager {
    private static final Logger log = LoggerFactory.getLogger(RerankManager.class);
    private static final String DEFAULT_ADAPTER = LocalRerankAdapter.class.getName();
    private static final RerankManager INSTANCE = new RerankManager();

    private volatile IRerankAdapter adapter;
    private boolean initialized;

    RerankManager() {
    }

    public static RerankManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(RerankConfig config) {
        if (initialized) {
            log.info("Rerank adapter is already initialized; restart is required to apply configuration changes");
            return;
        }
        initialized = true;

        String adapterClassName = config == null || isBlank(config.getAdapter())
                ? DEFAULT_ADAPTER : config.getAdapter().trim();
        RerankConfig effectiveConfig = config == null ? new RerankConfig() : config;
        effectiveConfig.setAdapter(adapterClassName);

        try {
            ContextLoader.registerExtensionLoadableClass(adapterClassName);
            Class<?> adapterClass = ContextLoader.getClass(adapterClassName);
            if (!IRerankAdapter.class.isAssignableFrom(adapterClass)) {
                throw new IllegalArgumentException("Configured class does not implement IRerankAdapter");
            }
            IRerankAdapter configuredAdapter = (IRerankAdapter) adapterClass.newInstance();
            configuredAdapter.initialize(effectiveConfig);
            adapter = configuredAdapter;
            log.info("Rerank adapter initialized: {}", adapterClassName);
        } catch (Exception e) {
            adapter = null;
            log.warn("Failed to initialize rerank adapter {}: {}",
                    adapterClassName, e.getMessage());
        }
    }

    public IRerankAdapter getAdapter() {
        return adapter;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
