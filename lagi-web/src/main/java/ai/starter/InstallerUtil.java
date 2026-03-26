package ai.starter;

import lombok.extern.slf4j.Slf4j;

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
public class InstallerUtil {
    public static final String CONFIG_FILE_PROPERTY = "linkmind.config";
    private static final String EXPORT_TO_OPENCLAW_ARG = "--export-to-openclaw=";
    private static final String IMPORT_FROM_OPENCLAW_ARG = "--import-from-openclaw=";
    private static final String LAGI_YML = "lagi.yml";
    private static final String SQLITE_RESOURCE_DIR = "sqlite";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";

    public static void main(String[] args) throws IOException {
        File jarFile = InstallerUtil.getJarFile();
        InstallerUtil.applyConfigAndDataDir(args, jarFile);
        boolean exportToOpenClaw = resolveExportToOpenClaw(args);
        boolean importFromOpenClaw = resolveImportFromOpenClaw(args);
        OpenClawUtil.sync(DEFAULT_PORT, exportToOpenClaw, importFromOpenClaw);
    }

    public static int resolvePort(String[] args) {
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

    /**
     * Binds Tomcat; override with --host=, server.address, or SERVER_ADDRESS.
     */
    public static String resolveHost(String[] args) {
        String fromArg = InstallerUtil.parseArg(args, "--host=");
        if (fromArg != null && !fromArg.isEmpty()) {
            return fromArg;
        }
        String envHost = System.getProperty("server.address", System.getenv("SERVER_ADDRESS"));
        if (envHost != null && !envHost.isEmpty()) {
            return envHost.trim();
        }
        return DEFAULT_HOST;
    }

    public static boolean resolveExportToOpenClaw(String[] args) {
        return Boolean.parseBoolean(parseArg(args, EXPORT_TO_OPENCLAW_ARG));
    }

    public static boolean resolveImportFromOpenClaw(String[] args) {
        return Boolean.parseBoolean(parseArg(args, IMPORT_FROM_OPENCLAW_ARG));
    }

    public static boolean resolveEnableSync(String[] args) {
        String val = parseArg(args, "--enable-sync=");
        if (val == null) {
            val = System.getProperty("enable.sync", System.getenv("ENABLE_SYNC"));
        }
        if (val != null && !val.isEmpty()) {
            return Boolean.parseBoolean(val);
        }
        return true;
    }

    public static File getJarFile() {
        try {
            URL location = InstallerUtil.class.getProtectionDomain().getCodeSource().getLocation();
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

    /**
     * Parses --config= and --data-dir= (or system property / env), applies defaults for IDE vs JAR,
     * copies resources when running from JAR and default dirs are used, then sets system properties.
     */
    public static void applyConfigAndDataDir(String[] args, File jarFile) throws IOException {
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
                copyResourceIfAbsent(InstallerUtil.class.getClassLoader(), LAGI_YML, configDir.resolve(LAGI_YML));
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

    public static String parseArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * Resolves the filesystem path of a classpath resource (for IDE run).
     */
    private static Path getResourceDir(String resourceName) {
        URL url = InstallerUtil.class.getClassLoader().getResource(resourceName);
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

    /**
     * Copies sqlite/* from the JAR into dataDir (e.g. saas.db). Skips if target already exists.
     */
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
