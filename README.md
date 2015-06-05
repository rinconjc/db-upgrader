# db-upgrader
A tiny library to automatically deploy database changes during application start-up. Useful for versioning database changes, and linking application aftifacts to specific database versions, and having these changes executed by the application itself, rather than by the usual manual deployment.
# Installation

Add the following repository to your pom.xml or Maven settings.xml (This is to enable pulling dependencies straight from GitHub)

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
    <snapshots>
        <enabled>false</enabled>
    </snapshots>
</repository>
```

Add the following dependency to your pom.xml

```xml
<dependency>
    <groupId>com.github.rinconjc</groupId>
    <artifactId>db-upgrader</artifactId>
    <version>1.0-beta5</version>
</dependency>
```


# Usage

## Setting up Database scripts

Use the following convention to setup your Dabase scritps. The scripts can contain any number of valid SQL statements, separated by semi-colon (;), can include single line comments (starting with --) or block comments (between /* and */)

The scripts should be created as resource files available in the classpath of the application, e.g. the *src/main/resources* folder for typical maven projects.

* *src/main/resources/db/latest/current.sql* The latest script to fully initialise a new database, it should be kept always up to date. If no automated initial setup is required it may contain an empty file.
* *src/main/resources/db/[env]/current.sql* The [env] specific initialisation script. The [env] cand be any string identifier e.g. dev, test, prod
* *src/main/resources/db/v[version#]/upgrade.sql* The SQL script to upgrade the database to version *version#* (an integer starting on 1)
* *src/main/resources/db/v[version#]/rollback.sql* The SQL script to reverse or rollback the changes of the *version#*, this is used to validate the upgrade script and if necessary downgrade the database.
* *src/main/resources/db/v[version#]/[env]/upgrade.sql* (Optional) The upgrade script only applicable to the given environment [env]
* *src/main/resources/db/v[version#]/[env]/rollback.sql* (Optional) The environment specific reverse script.

## Upgrade Automation
Use the *DbUpgrader* to perform the database upgrade during the application startup.

```java
import com.rinconj.dbupgrader.DbUpgrader;
...
int DB_VERSION = 4;

public void applicationStartup(){
    DbUpgrader dbUpgrader = new DbUpgrader(dataSource, "dev");
    dbupgrader.syncToVersion(DB_VERSION, false, true);
}
```

That's all that is required to synchronize database changes up to the specified version. Every version scripts will be executed only once for each database.

## Upgrade/rollback Validation

It's recommendable to test/validate the upgrade/rollback scripts before the actual deployment of the application. For this purpose, a convenience method *validateVersion* is provided in DbUpgrader.

 The following provides a snippet of a unit test to validate the execution of the upgrade/rollback scripts. Any syntax errors, or other SQL failures will cause the test to fail. The test should be performed against a database that reflects the real application database, e.g. integration test database.
```java

public class DbUpgradeTest{

    public void testDbUpgrade(){
        DbUpgrader dbUpgrader = new DbUpgrader(dataSource, "dev");
        dbupgrader.validateVersion(DB_VERSION, false, true);
    }
}

```
## Customisations

The above conventions and defaults can be customised as follows:
  
  * Using a different statement separator. *Warning*: Only ; and / has been tested, use other separators at own risk.  
   
   ```java
   dbUpgrader.setStatementSeparator('/');
   ```
  
  * Use a different script directory.  The default directory is */db*, but can be changed to any other by setting the *scriptsBasePath*
  
   ```java
   dbUpgrader.setScriptsBasePath("/my-custom-sql-resource-dir");   
   ```
   
   * Store SQL scripts NOT in the classpath. By default the SQL resources are looked up in the classpath, but it's also possible to store in directories outside of the classpath, by specifying the location via the *dbupgrader.sql.dir* JVM parameter:
    e.g.
    
    ```bash
    java -Ddbupgrader.sql.dir=/path/to/my/sql/files ...
    ```
    
    
   




