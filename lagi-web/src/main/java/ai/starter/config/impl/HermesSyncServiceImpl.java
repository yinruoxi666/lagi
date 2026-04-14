package ai.starter.config.impl;

import ai.starter.config.IConfigSyncService;

public class HermesSyncServiceImpl implements IConfigSyncService {
    @Override
    public boolean check() {
        // TODO 2026/4/13 Hermes inject
        return true;
    }

    @Override
    public void export(String path) {
        System.out.println("HermesSyncServiceImpl.export");
    }

    @Override
    public void load() {
        System.out.println("HermesSyncServiceImpl.load");
    }

    @Override
    public String name() {
        return "Hermes";
    }
}
