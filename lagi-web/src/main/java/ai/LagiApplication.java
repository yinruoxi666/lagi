package ai;


import ai.utils.CommandLineOptions;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.file.Files;

public class LagiApplication {

    private static final Logger logger = LoggerFactory.getLogger(LagiApplication.class);

    private static final String SYS_PROP_SQLITE_DB = "linkmind.sqlite";
    private static final String SYS_PROP_CONFIG = "linkmind.config";
    private static final String SYS_PROP_APP_HOME = "linkmind.home";

    private static final String WEBAPP_DIR_NAME = "webapp";
    private static final String WEB_XML_PATH = "WEB-INF/web.xml";
    private static final String TOMCAT_DIR_PREFIX = "tomcat-";
    private static final String WORKSPACE_DIR = "workspace";

    private static final int DEFAULT_PORT = 8080;

    private static final String WEBAPP_ZIP_RESOURCE = "/webapp.zip";

    public static void main(String[] args) {
        try {

            CommandLineOptions options = CommandLineOptions.parse(args);

            initSystemProperties(options);

            int port = options.getPortOrDefault(DEFAULT_PORT);

            File appHomeDir = getAppHomeDir();

            File webappDir = findOrExtractWebappDir(appHomeDir);
            if (webappDir == null) {
                printError("Webapp directory not found, startup aborted");
                printWebappSearchPaths(appHomeDir);
                return;
            }

            Tomcat tomcat = configureTomcat(port, appHomeDir, webappDir, options);
            startTomcat(tomcat, appHomeDir, webappDir, port, options);

        } catch (Exception e) {
            printError("Application startup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /** Initialize system properties from command line options. */
    private static void initSystemProperties(CommandLineOptions options) {
        if (options.getSqliteDbPath() != null) {
            System.setProperty(SYS_PROP_SQLITE_DB, options.getSqliteDbPath());
        }
        if (options.getConfigPath() != null) {
            System.setProperty(SYS_PROP_CONFIG, options.getConfigPath());
        }
    }

    /** Resolve application home directory (APP_HOME). */
    private static File getAppHomeDir() throws Exception {
        String home = System.getProperty(SYS_PROP_APP_HOME);
        if (home != null && !home.trim().isEmpty()) {
            return new File(home.trim()).getCanonicalFile();
        }
        return new File(".").getCanonicalFile();
    }

    /** Find web application directory. Only use ${APP_HOME}/webapp, extract webapp.zip if necessary. */
    private static File findOrExtractWebappDir(File appHomeDir) {
        File extracted = new File(appHomeDir, WEBAPP_DIR_NAME);
        File webXml = new File(extracted, WEB_XML_PATH);
        if (!webXml.exists()) {
            boolean ok = extractWebappZipIfPresent(extracted);
            if (!ok) {
                return null;
            }
        }
        if (!new File(extracted, WEB_XML_PATH).exists()) {
            return null;
        }
        return extracted;
    }

    /** Extract /webapp.zip from classpath to targetDir (e.g. ${APP_HOME}/webapp). */
    private static boolean extractWebappZipIfPresent(File targetDir) {
        try (InputStream in = LagiApplication.class.getResourceAsStream(WEBAPP_ZIP_RESOURCE)) {
            if (in == null) {
                return false;
            }
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new RuntimeException("Failed to create webapp directory: " + targetDir.getAbsolutePath());
            }

            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    File outFile = new File(targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        if (!outFile.exists() && !outFile.mkdirs()) {
                            throw new RuntimeException("Failed to create dir: " + outFile.getAbsolutePath());
                        }
                        continue;
                    }
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new RuntimeException("Failed to create dir: " + parent.getAbsolutePath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }

            return new File(targetDir, WEB_XML_PATH).exists();
        } catch (Exception e) {
            logger.error("Failed to extract webapp.zip to: {}", targetDir.getAbsolutePath(), e);
            return false;
        }
    }

    /** Configure embedded Tomcat instance: port, baseDir, and web application context. */
    private static Tomcat configureTomcat(int port, File appHomeDir, File webappDir, CommandLineOptions options) {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);

        // 配置 Tomcat 工作目录
        File tomcatBaseDir = getTomcatBaseDir(appHomeDir, port);
        tomcat.setBaseDir(tomcatBaseDir.getAbsolutePath());

        String contextPath = "";
        String docBase = webappDir.getAbsolutePath();
        Context context = tomcat.addWebapp(contextPath, docBase);
        
        if (options.getSqliteDbPath() != null) {
            context.getServletContext().setAttribute("sqlite.db.path", options.getSqliteDbPath());
        }

        return tomcat;
    }

    /** Get Tomcat base directory: use ${APP_HOME}/workspace/tomcat-${port}. */
    private static File getTomcatBaseDir(File appHomeDir, int port) {
        File tomcatBaseDir = new File(new File(appHomeDir, WORKSPACE_DIR), TOMCAT_DIR_PREFIX + port);
        ensureDirectoryExists(tomcatBaseDir, "Tomcat workspace directory");
        return tomcatBaseDir;
    }

    /** Ensure directory exists; create if necessary, throw RuntimeException on failure. */
    private static void ensureDirectoryExists(File dir, String dirDesc) {
        if (dir == null) {
            throw new IllegalArgumentException(dirDesc + " path must not be null");
        }
        if (!dir.exists()) {
            try {
                Files.createDirectories(dir.toPath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to create " + dirDesc + ": " + dir.getAbsolutePath(), e);
            }
        }
        if (!dir.isDirectory()) {
            throw new RuntimeException(dirDesc + " is not a directory: " + dir.getAbsolutePath());
        }
    }

    /** Start embedded Tomcat and block current thread. */
    private static void startTomcat(Tomcat tomcat, File appHomeDir, File webappDir, int port, CommandLineOptions options) throws Exception {
        // Print startup information
        printStartupInfo(appHomeDir, webappDir, port, options);
        // Start Tomcat
        tomcat.start();
        tomcat.getServer().await();
    }


    /** Print startup information to logs. */
    private static void printStartupInfo(File appHomeDir, File webappDir, int port, CommandLineOptions options) {
        String separator = "==================================================";
        printInfo(separator);
        printInfo("LinkMind Start");
        printInfo("APP_HOME   : " + appHomeDir.getAbsolutePath());
        printInfo("WEBAPP_HOME: " + webappDir.getAbsolutePath());
        printInfo("Port       : " + port);
        printInfo("SQLite Path: " + (System.getProperty(SYS_PROP_SQLITE_DB) != null ?
                System.getProperty(SYS_PROP_SQLITE_DB) : "(not specified, use default)"));
        printInfo("Config     : " + (System.getProperty(SYS_PROP_CONFIG) != null ?
                System.getProperty(SYS_PROP_CONFIG) : "(not specified, use default search logic)"));
        printInfo("WEB-INF/web.xml will be used to configure all Servlets/Filters.");
        printInfo(separator);
    }

    /** Print all candidate webapp locations when startup fails. */
    private static void printWebappSearchPaths(File appHomeDir) {
        File baseDir;
        try {
            baseDir = new File(".").getCanonicalFile();
        } catch (Exception e) {
            baseDir = new File(".");
        }
        logger.error("webapp directory not found. Tried:");
        logger.error("  {}", new File(baseDir, "src/main/webapp").getAbsolutePath());
        logger.error("  {}", new File(baseDir, "lagi-web/src/main/webapp").getAbsolutePath());
        logger.error("  {}", new File(baseDir, "webapp").getAbsolutePath());
        logger.error("  {} (extract from classpath:webapp.zip)", new File(appHomeDir, "webapp").getAbsolutePath());
    }

    private static void printInfo(String msg) {
        logger.info(msg);
    }

    private static void printError(String msg) {
        logger.error(msg);
    }

}
