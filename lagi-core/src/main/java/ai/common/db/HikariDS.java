package ai.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariDS {
    private static final HikariConfig landingbjConfig;
    private static final HikariDataSource landingbjDS;
    private static final String LANDINGBJ_CONFIG_PATH = "/hikari-saas.properties";

    static {
        landingbjConfig = new HikariConfig(LANDINGBJ_CONFIG_PATH);
        landingbjDS = new HikariDataSource(landingbjConfig);
//        initializeDatabase("/init.sql");
    }

    private HikariDS() {
    }

    public static Connection getConnection(String conname) throws SQLException {
        Connection conn = null;
        if (conname.equals(landingbjConfig.getPoolName())) {
            conn = landingbjDS.getConnection();
        }
        return conn;
    }
}