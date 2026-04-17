package ai.starter.config.impl;

import ai.starter.config.IConfigSyncService;

public abstract class BaseSyncServiceImpl implements IConfigSyncService {

    protected String basePath;

    public BaseSyncServiceImpl(String basePath) {
        this.basePath = basePath;
    }

}
