
package com.rinconj.dbupgrader;

import javax.sql.DataSource;
import java.io.*;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.rinconj.dbupgrader.JdbcUtils.collectFirst;
import static com.rinconj.dbupgrader.JdbcUtils.executeQuery;
import static com.rinconj.dbupgrader.JdbcUtils.executeSql;
import static java.lang.String.format;
import static java.lang.String.valueOf;

/**
 * Upgrades/downgrades a database based on versioned SQL scripts. The following conventions are used:
 * <ul>
 * <li>A <b>current.sql</b> script contains the up-to-date version of the database setup script</li>
 * <li>Database changes are versioned using consecutive integers (0...)</li>
 * <li>Every upgrade may contain the following files: upgrade.sql, rollback.sql </li>
 * <li>Script can be customized per environment e.g. dev, test, prod, etc. </li>
 * <li>The change scripts should be created in the following directory structure:
 * <pre>
 *     [base_dir]/latest/current.sql
 *     [base_dir]/latest/[env]/current.sql
 *     [base_dir]/v[version_id]/[upgrade.sql, rollback.sql]
 *     [base_dir]/v[version_id]/[env]/[upgrade.sql, rollback.sql]
 * </pre>
 * </li>
 * </ul>
 *
 */
public class DbUpgrader {
    private final static Logger LOGGER = Logger.getLogger(DbUpgrader.class.getName());
    private final static String SQL_SCRIPTS_DIR_SYS_PROPERTY = "dbupgrader.sql.dir";
    private static final String ADD_LAST_SYNC_ALTER_TABLE = "ALTER TABLE %s ADD LAST_SYNC TIMESTAMP";
    private static final String CREATE_VERSION_TABLE_SQL = "CREATE TABLE %s(id varchar(100) NOT NULL, version INTEGER NOT NULL, last_sync TIMESTAMP, " +
            "CONSTRAINT unique_dbinfo_id PRIMARY KEY (id))";

    private String scriptsBasePath = "/db";

    private char statementSeparator = ';';

    private final DataSource dataSource;

    private String schemaId = "default";

    private String environment;

    private String versionTable = "DB_VERSION";

    private String scriptFileFormat = "v%version/%type.sql";
    private String scriptEnvFileFormat = "v%version/%env/%type.sql";

    private File scriptDir;

    public DbUpgrader(DataSource dataSource, String environment) {
        this.dataSource = dataSource;
        this.environment = environment;

        String scriptDir = System.getProperty(SQL_SCRIPTS_DIR_SYS_PROPERTY);
        if (scriptDir != null) {
            File dir = new File(scriptDir);
            if (!dir.exists() || !dir.isDirectory())
                LOGGER.warning("The specified SQL script dir " + scriptDir + ", doesn't exist");
            else
                this.scriptDir = dir;
        }
    }


    /**
     * Applies upgrade DB scripts up to the specified version. It expects that <b>upgrade_(version)</b> and <b>downgrade_(version)</b>
     * are present in the classpath of the system property specified directory.
     *
     * @param version
     */
    public void syncToVersion(int version, boolean allowDowngrade, boolean emptyDb) throws Exception {
        Connection con = null;
        try {
            con = dataSource.getConnection();
            DatabaseMetaData metaData = con.getMetaData();
            LOGGER.info(format("executing DB version sync on DB: %s@%s", metaData.getUserName(), metaData.getURL()));
            ResultSet rs = metaData.getTables(null, null, versionTable, null);
            int dbCurVersion;
            if (rs.next()) {
                dbCurVersion = ((Number)collectFirst(executeQuery(con, "select version from " + versionTable + " WHERE id=?", schemaId), 0)).intValue();
                Timestamp lastSync = getLastSync(con);
                LOGGER.info("last sync was on " + lastSync);
            } else {
                //empty db: setup versioning and current sql
                con.createStatement().execute(format(CREATE_VERSION_TABLE_SQL, versionTable));
                if (emptyDb) {
                    LOGGER.info("Apparently an empty schema...applying current script");
                    //apparently an empty db
                    execScript(con, "latest/current.sql", false);
                    execScript(con, "latest/" + environment + "/current.sql", true);
                    executeSql(con, format("INSERT INTO %s(id,version, last_sync) values(?,?,?)", versionTable), schemaId, version, new Timestamp(System.currentTimeMillis()));
                    dbCurVersion = version;
                } else {
                    executeSql(con, format("INSERT INTO %s(id,version, last_sync) values(?,?,?)", versionTable), schemaId, 0, new Timestamp(System.currentTimeMillis()));
                    dbCurVersion = 0;
                }
            }

            if (version > dbCurVersion) {
                for (int v = dbCurVersion + 1; v <= version; v++) {
                    LOGGER.info(format("Upgrading DB from %d to %d", v - 1, v));
                    //execute common upgrade
                    execScript(con, scriptFileFormat.replaceAll("%version", valueOf(v)).replaceAll("%type", "upgrade"), true);
                    //execute env specific upgrade
                    execScript(con, scriptEnvFileFormat.replaceAll("%version", valueOf(v)).replaceAll("%env", environment).replaceAll("%type", "upgrade"), true);
                    //update version in DB
                    executeSql(con, format("UPDATE %s set version=?, last_sync=? where id = ?", versionTable), v, new Timestamp(System.currentTimeMillis()) ,schemaId);
                }
            } else if (version < dbCurVersion && allowDowngrade) {
                LOGGER.info("downgrading from " + dbCurVersion + " to " + version);
                for (int v = dbCurVersion; v > version; v--) {
                    LOGGER.info("Downgrading DB from version " + v);
                    //execute env specific downgrade
                    execScript(con, scriptEnvFileFormat.replaceAll("%version", valueOf(v)).replaceAll("%env", environment).replaceAll("%type", "rollback"), true);
                    //execute common downgrade
                    execScript(con, scriptFileFormat.replaceAll("%version", valueOf(v)).replaceAll("%type", "rollback"), true);
                    //update version in DB
                    executeSql(con, format("UPDATE %s set version=?, last_sync=? where id = ?", versionTable), v - 1, new Timestamp(System.currentTimeMillis()), schemaId);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed upgrading DB", e);
            throw new Exception(e);
        } finally {
            if (con != null) try {
                con.close();
            } catch (SQLException e) {
            }
        }
    }

    private Timestamp getLastSync(Connection con) {
        try{
            ResultSet rs = executeQuery(con, format("SELECT LAST_SYNC FROM %s WHERE id=?", versionTable), schemaId);
            return rs.next()? rs.getTimestamp(1) : null;
        }catch (Exception e){
            LOGGER.info("upgrading version table...");
            executeSql(con, format(ADD_LAST_SYNC_ALTER_TABLE, versionTable));
        }
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Retrieves the current version number of the database.
     * @return
     */
    public int getCurrentDbVersion(){
        Connection con = null;
        try{
            con = dataSource.getConnection();
            if(con.getMetaData().getTables(null, null, versionTable, null).next()){
                return ((Number)collectFirst(executeQuery(con, "select version from " + versionTable + " WHERE id=?", schemaId), 0)).intValue();
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed retrieving current db version", e);
        } finally {
            if(con != null) try {
                con.close();
            } catch (SQLException e) {
            }
        }
    }

    /**
     * Validates the upgrade/rollback scripts for the given version. Useful for unit testing version scripts.
     * @param version
     * @throws Exception if upgrade or rollback fails
     */
    public void validateVersion(int version, boolean isEmptyDb) throws Exception {
        //test upgrade by running current, then downgrading and finally upgrading it back
        int dbVersion = getCurrentDbVersion();
        if(dbVersion == version){
            syncToVersion(version - 1, true, false);
        } else {
            syncToVersion(version, true, isEmptyDb);
        }
        //restore db version
        syncToVersion(dbVersion, true, false);
    }

    /**
     * Executes the specified SQL scripts.
     *
     * @param conn
     * @param resource
     * @param ignoreIfNotFound
     * @throws IOException
     */
    void execScript(Connection conn, String resource, boolean ignoreIfNotFound) throws IOException, SQLException {
        InputStream is;
        if (scriptDir != null) {
            File file = new File(scriptDir, resource);
            is = file.isFile() ? new FileInputStream(file) : null;
        } else {
            is = getClass().getResourceAsStream(scriptsBasePath + "/" + resource);
        }

        if (is == null && !ignoreIfNotFound) throw new IOException("SQL Script Resource " + resource + " not found!");
        if (is == null) return;
        LOGGER.info("Executing " + resource);
        StatementIterator iterator = new StatementIterator(new InputStreamReader(is), statementSeparator);
        while (iterator.hasNext()) {
            String stmt = iterator.next();
            try {
                LOGGER.info("executing: " + stmt);
                Statement statement = conn.createStatement();
                boolean hasResult = statement.execute(stmt);
                if(!hasResult){
                    LOGGER.info(statement.getUpdateCount() + " row(s) affected");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed executing statement:" + stmt, e);
                throw e;
            }
        }
    }

    public String getScriptsBasePath() {
        return scriptsBasePath;
    }

    public void setScriptsBasePath(String scriptsBasePath) {
        this.scriptsBasePath = scriptsBasePath;
    }

    public char getStatementSeparator() {
        return statementSeparator;
    }

    public void setStatementSeparator(char statementSeparator) {
        this.statementSeparator = statementSeparator;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }
}


class JdbcUtils {

    public static <T> T collectFirst(ResultSet rs, T defaultValue) throws SQLException {
        if (!rs.next()) return defaultValue;
        return (T) rs.getObject(1);
    }

    public static ResultSet executeQuery(Connection conn, String sql, Object... args) {
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            int i = 0;
            for (Object arg : args) {
                if (arg == null)
                    ps.setNull(++i, Types.CHAR);
                else
                    ps.setObject(++i, arg);
            }
            return ps.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException("failed executing query :" + sql, e);
        }
    }

    public static void executeSql(Connection conn, String sql, Object... args) {
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            int i = 0;
            for (Object arg : args) {
                if (arg == null)
                    ps.setNull(++i, Types.CHAR);
                else
                    ps.setObject(++i, arg);
            }
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("failed executing query :" + sql, e);
        }
    }

}
