package ai.starter;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public final class TomcatUtil {

    private static final int MAX_TEMP_DIRS = 10;
    private static final String TOMCAT_BASE_PREFIX = "tomcat-base-";
    private static final String LAGI_WEBAPP_PREFIX = "lagi-webapp-";
    private static final String WEBAPP_ZIP = "webapp.zip";
    private static final String DEV_WEBAPP_PATH = "lagi-web/src/main/webapp";

    private TomcatUtil() {
    }

    /**
     * Starts embedded Tomcat and blocks until the server stops.
     *
     * @param host      bind address (e.g. localhost, 0.0.0.0)
     * @param port      server port
     * @param webappDir webapp root directory
     * @param devMode   if true, context is reloadable (e.g. for IDE)
     */
    public static void startAndAwait(String host, int port, Path webappDir, boolean devMode) throws Exception {
        Path baseDir = createTempDirWithLimit(TOMCAT_BASE_PREFIX);

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(baseDir.toAbsolutePath().toString());
        Connector connector = tomcat.getConnector();
        if (host != null && !host.isEmpty()) {
            connector.setProperty("address", host);
        }

        Context ctx = tomcat.addWebapp("", webappDir.toAbsolutePath().toString());
        ctx.setReloadable(devMode);
        if (devMode) {
            log.info("Webapp reload enabled (dev mode): changes under webapp will be picked up automatically.");
        }

        StandardJarScanner jarScanner = (StandardJarScanner) ctx.getJarScanner();
        jarScanner.setScanClassPath(false);
        jarScanner.setScanBootstrapClassPath(false);
        jarScanner.setScanAllDirectories(false);
        jarScanner.setScanAllFiles(false);

        tomcat.start();
        String logHost = (host == null || host.isEmpty()) ? "0.0.0.0" : host;
        log.info("Server started on http://{}:{}", logHost, port);
        tomcat.getServer().await();
    }

    /**
     * Resolves the webapp root directory.
     * When running from the shaded JAR the webapp is bundled as webapp.zip on the
     * classpath; we extract it to a temporary directory.
     * When running directly from the IDE (no JAR), we fall back to the source tree.
     */
    public static Path resolveWebappDir(Class<?> clazz) throws IOException {
        URL zipUrl = clazz.getClassLoader().getResource(WEBAPP_ZIP);
        if (zipUrl != null) {
            return extractZip(zipUrl);
        }
        File devWebapp = new File(DEV_WEBAPP_PATH);
        if (devWebapp.isDirectory()) {
            return devWebapp.toPath();
        }
        throw new IllegalStateException(
                "Cannot locate webapp: neither '" + WEBAPP_ZIP
                        + "' on classpath nor '" + DEV_WEBAPP_PATH + "' directory found.");
    }

    /**
     * Creates a temp directory with the given prefix. Keeps at most {@value #MAX_TEMP_DIRS}
     * directories matching the prefix; removes the oldest ones first.
     */
    public static Path createTempDirWithLimit(String prefix) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        List<Path> existing;
        try (Stream<Path> stream = Files.list(tempDir)) {
            existing = stream
                    .filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .collect(Collectors.toList());
        }
        int toRemove = existing.size() - MAX_TEMP_DIRS + 1;
        for (int i = 0; i < toRemove && i < existing.size(); i++) {
            Path old = existing.get(i);
            try {
                deleteRecursively(old);
                log.debug("Removed old temp dir: {}", old);
            } catch (IOException e) {
                log.warn("Could not remove old temp dir {}: {}", old, e.getMessage());
            }
        }
        return Files.createTempDirectory(prefix);
    }

    private static Path extractZip(URL zipUrl) throws IOException {
        Path targetDir = createTempDirWithLimit(LAGI_WEBAPP_PREFIX);
        try (InputStream is = zipUrl.openStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return targetDir;
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
