package ai.starter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
public class Application {
    /** System property: full path to lagi.yml (used by ContextLoader and FilterConfigServlet). */
    public static final String CONFIG_FILE_PROPERTY = "linkmind.config";

    private static final int DEFAULT_PORT = 8080;
    private static final String LAGI_YML = "lagi.yml";
    private static final String SQLITE_RESOURCE_DIR = "sqlite";

    public static void main(String[] args) throws Exception {
        File jarFile = getJarFile();
        applyConfigAndDataDir(args, jarFile);

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        int port = resolvePort(args);

        OpenClawUtil.sync(port);

        Path webappDir = TomcatUtil.resolveWebappDir(Application.class);
        boolean devMode = jarFile == null;

        TomcatUtil.startAndAwait(port, webappDir, devMode);
    }

    /**
     * Parses --config= and --data-dir= (or system property / env), applies defaults for IDE vs JAR,
     * copies resources when running from JAR and default dirs are used, then sets system properties.
     */
    private static void applyConfigAndDataDir(String[] args, File jarFile) throws IOException {
        String configArg = parseArg(args, "--config=");
        if (configArg == null) configArg = System.getProperty("config.dir", System.getenv("CONFIG_DIR"));
        String dataDirArg = parseArg(args, "--data-dir=");
        if (dataDirArg == null) dataDirArg = System.getProperty("data.dir", System.getenv("DATA_DIR"));

        Path configDir;
        Path dataDir;

        if (jarFile == null) {
            // IDE: defaults point to resources directory
            configDir = configArg != null && !configArg.isEmpty()
                    ? Paths.get(configArg).toAbsolutePath().normalize()
                    : getResourceDir(LAGI_YML);
            dataDir = dataDirArg != null && !dataDirArg.isEmpty()
                    ? Paths.get(dataDirArg).toAbsolutePath().normalize()
                    : getResourceDir(SQLITE_RESOURCE_DIR + "/saas.db");
        } else {
            // JAR: defaults are jarDir/config and jarDir/data; copy from resources if missing
            Path jarDir = jarFile.getParentFile().toPath();
            configDir = configArg != null && !configArg.isEmpty()
                    ? Paths.get(configArg).toAbsolutePath().normalize()
                    : jarDir.resolve("config");
            dataDir = dataDirArg != null && !dataDirArg.isEmpty()
                    ? Paths.get(dataDirArg).toAbsolutePath().normalize()
                    : jarDir.resolve("data");

            if (configArg == null || configArg.isEmpty()) {
                Files.createDirectories(configDir);
                copyResourceIfAbsent(Application.class.getClassLoader(), LAGI_YML, configDir.resolve(LAGI_YML));
            }
            if (dataDirArg == null || dataDirArg.isEmpty()) {
                Files.createDirectories(dataDir);
                copySqliteToDataDir(jarFile.toPath(), dataDir);
            }
        }

        System.setProperty(CONFIG_FILE_PROPERTY, configDir.resolve(LAGI_YML).toString());
        System.setProperty(ai.common.db.HikariDS.DATA_DIR_PROPERTY, dataDir.toString());
        log.debug("Config: {} (lagi.yml)", configDir);
        log.debug("Data dir: {} (saas.db)", dataDir);
    }

    private static String parseArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** Resolves the filesystem path of a classpath resource (for IDE run). */
    private static Path getResourceDir(String resourceName) {
        URL url = Application.class.getClassLoader().getResource(resourceName);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException("Cannot resolve resource path for: " + resourceName + " (not a file URL)");
        }
        try {
            Path path = Paths.get(url.toURI());
            return path.getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve resource path for: " + resourceName, e);
        }
    }

    /** Copies sqlite/* from the JAR into dataDir (e.g. saas.db). Skips if target already exists. */
    private static void copySqliteToDataDir(Path jarPath, Path dataDir) throws IOException {
        String prefix = SQLITE_RESOURCE_DIR + "/";
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                String rel = e.getName().substring(prefix.length());
                Path dest = dataDir.resolve(rel).normalize();
                if (!dest.startsWith(dataDir)) continue;
                if (Files.exists(dest)) continue;
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static int resolvePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        String envPort = System.getProperty("server.port", System.getenv("SERVER_PORT"));
        if (envPort != null && !envPort.isEmpty()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }

    private static File getJarFile() {
        try {
            URL location = Application.class.getProtectionDomain().getCodeSource().getLocation();
            URI uri = location.toURI();
            Path jarPath;
            if ("jar".equals(uri.getScheme())) {
                String ssp = uri.getRawSchemeSpecificPart();
                int excl = ssp.indexOf('!');
                String filePath = excl >= 0 ? ssp.substring(0, excl) : ssp;
                jarPath = Paths.get(URI.create(filePath));
            } else {
                jarPath = Paths.get(uri);
            }
            File f = jarPath.toFile();
            return f.isFile() ? f : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static void copyResourceIfAbsent(ClassLoader cl, String resourceName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        URL url = cl.getResource(resourceName);
        if (url == null) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (InputStream in = url.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
