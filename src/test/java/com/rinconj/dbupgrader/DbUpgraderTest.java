package com.rinconj.dbupgrader;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static com.rinconj.dbupgrader.JdbcUtils.collectFirst;
import static com.rinconj.dbupgrader.JdbcUtils.executeSql;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Created by julio on 4/05/15.
 */
public class DbUpgraderTest {

    DataSource getDataSource(String name){
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("admin");
        dataSource.setPassword("");
        return dataSource;
    }

    @Test
    public void shouldApplyCurrentSchemaOnEmptyDb() throws Exception {
        DataSource dataSource = getDataSource("test1");
        DbUpgrader dbUpgrader = new DbUpgrader(dataSource, "dev");
        Connection conn = dataSource.getConnection();
        dbUpgrader.syncToVersion(2, true, true);
        try{
            int dbver = collectFirst(conn.createStatement().executeQuery("select version from DB_VERSION where id='default'"), -1);
            assertEquals(2, dbver);
        }finally {
            conn.close();
        }
    }

    @Test
    public void shouldUpgradeDb() throws Exception {
        DataSource dataSource = getDataSource("test2");
        Connection conn = dataSource.getConnection();
        try {
            DbUpgrader dbUpgrader = new DbUpgrader(dataSource, "dev");
            dbUpgrader.syncToVersion(1,true, false);
            assertEquals(1, (int) collectFirst(conn.createStatement().executeQuery("select version from DB_VERSION where id = 'default'"), -1));
            conn.createStatement().executeQuery("select * from tab3");
        } finally {
            conn.close();
        }
    }

    @Test
    public void shouldDowngradeDb() throws Exception {
        DataSource dataSource = getDataSource("test3");
        Connection conn = dataSource.getConnection();
        try {
            DbUpgrader dbUpgrader = new DbUpgrader(dataSource, "dev");
            dbUpgrader.syncToVersion(2,true, true);//applies current.sql (version 2)
            dbUpgrader.syncToVersion(0,true, true); //downgrades (applies rollback 2)
            assertEquals(0, (int) collectFirst(conn.createStatement().executeQuery("select version from DB_VERSION where id = 'default'"), -1));
            assertFalse(conn.getMetaData().getTables(null, null, "TAB3", null).next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void shouldValidateVersion() throws Exception {
        DataSource dataSource = getDataSource("test4");
        DbUpgrader upgrader = new DbUpgrader(dataSource, "dev");
        upgrader.validateVersion(2, true);
        upgrader.getCurrentDbVersion();
    }

    @Test
    public void shouldHandleOldVersionTable() throws Exception {
        DataSource dataSource = getDataSource("test5");
        Connection con = dataSource.getConnection();
        executeSql(con, "create table db_version(id varchar(100) not null, version integer not null, constraint db_version_pk primary key(id))");
        executeSql(con, "insert into db_version(id, version) values(?,?)", "default", 1);
        DbUpgrader upgrader = new DbUpgrader(dataSource, "dev");
        upgrader.syncToVersion(1, false, false);
        assertEquals(1, upgrader.getCurrentDbVersion());
    }
}
