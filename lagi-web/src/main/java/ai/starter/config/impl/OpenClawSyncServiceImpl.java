package ai.starter.config.impl;

import ai.starter.OpenClawInjector;
import ai.starter.OpenClawUtil;
import ai.starter.config.IConfigSyncService;

public class OpenClawSyncServiceImpl implements IConfigSyncService {
    @Override
    public boolean check() {
        return OpenClawUtil.openClawExists();
    }

    @Override
    public void export(String path) {
        OpenClawInjector.inject(path);
    }

    @Override
    public void load() {
        OpenClawUtil.syncToLinkMind();
    }

    @Override
    public String name() {
        return "OpenClaw";
    }
}
