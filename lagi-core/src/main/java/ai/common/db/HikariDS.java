package ai.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

public class HikariDS {
    /** System property key for data directory override (directory containing saas.db). */
    public static final String DATA_DIR_PROPERTY = "ai.data.dir";

    private static final HikariConfig saasConfig;
    private static final HikariDataSource saasDS;
    private static final String SAAS_CONFIG_PATH = "/hikari-saas.properties";
    private static final String SAAS_DB_FILE = "saas.db";

    static {
        saasConfig = new HikariConfig(SAAS_CONFIG_PATH);
        String dataDir = System.getProperty(DATA_DIR_PROPERTY);
        if (dataDir != null && !dataDir.isEmpty()) {
            Path dbPath = Paths.get(dataDir).resolve(SAAS_DB_FILE).toAbsolutePath().normalize();
            String jdbcUrl = "jdbc:sqlite:" + dbPath.toString().replace('\\', '/') + "?enable_load_extension=true";
            saasConfig.setJdbcUrl(jdbcUrl);
        }
        saasDS = new HikariDataSource(saasConfig);
    }

    private HikariDS() {
    }

    public static Connection getConnection(String conname) throws SQLException {
        Connection conn = null;
        if (conname.equals(saasConfig.getPoolName())) {
            conn = saasDS.getConnection();
        }
        return conn;
    }
}