package ai.starter.config.impl;

import ai.starter.config.IConfigSyncService;

public class DeepFlowSyncServiceImpl implements IConfigSyncService {
    @Override
    public boolean check() {
        // TODO 2026/4/13 deepFlow inject
        return true;
    }

    @Override
    public void export(String path) {
        System.out.println("DeepFlowSyncServiceImpl export");
    }

    @Override
    public void load() {
        System.out.println("DeepFlowSyncServiceImpl load");
    }

    @Override
    public String name() {
        return "DeepFlow";
    }
}
