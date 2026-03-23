package ai.starter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class Application {
    public static void main(String[] args) throws Exception {
        File jarFile = InstallerUtil.getJarFile();
        boolean devMode = jarFile == null;

        InstallerUtil.applyConfigAndDataDir(args, jarFile);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        int port = InstallerUtil.resolvePort(args);
        String host = InstallerUtil.resolveHost(args);

        if (!devMode) {
            OpenClawUtil.sync(port, true, true);
        }

        Path webappDir = TomcatUtil.resolveWebappDir(Application.class);
        TomcatUtil.startAndAwait(host, port, webappDir, devMode);
    }
}
