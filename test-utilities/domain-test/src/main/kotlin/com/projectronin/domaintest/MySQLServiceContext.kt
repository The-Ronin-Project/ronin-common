package com.projectronin.domaintest

import com.projectronin.database.helpers.MysqlVersionHelper
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import java.sql.DriverManager
import java.util.UUID

/**
 * A context for starting a MySQL service.  See [DomainTestSetupContext.withMySQL].
 */
@Suppress("SqlNoDataSourceInspection")
class MySQLServiceContext private constructor(private val network: Network, val rootPassword: String = "root") : DomainTestContainerContext {

    companion object {
        internal fun createInstance(network: Network, rootPassword: String): MySQLServiceContext {
            if (_instance == null) {
                _instance = MySQLServiceContext(network, rootPassword)
            }
            return _instance!!
        }

        private var _instance: MySQLServiceContext? = null
        internal val instance: MySQLServiceContext
            get() {
                if (_instance == null) {
                    throw IllegalStateException("No MySQL service has been configured")
                }
                return _instance!!
            }
    }

    private val dbs = mutableSetOf<DbConfig>()

    internal var container: MySQLContainer<*>? = null

    init {
        _instance = this
    }

    /**
     * Adds a database/username/password triple to be created after the container starts.  Will throw an exception
     * if it finds a duplicate (defined as same dbName or same username but where all three values do not match)
     */
    fun withDatabase(dbName: String, username: String = dbName, password: String = dbName) {
        val cfg = DbConfig(dbName, username, password)
        assert(!dbs.any { (it.dbName == dbName || it.username == username) && it != cfg }) { "Found a duplicate db or username to db=$dbName or user=$username" }
        dbs += cfg
    }

    override fun createContainer(): GenericContainer<*> {
        if (container == null) {
            val dbIdentifier = UUID.randomUUID().toString().replace("-", "").substring(0, 5)
            container = MySQLContainer(MysqlVersionHelper.MYSQL_VERSION_OCI)
                .withDatabaseName("test_$dbIdentifier")
                .withUsername("test_$dbIdentifier")
                .withPassword(rootPassword)
                .withNetwork(network)
                .withNetworkAliases(SupportingServices.MySql.containerName)
        }
        return container!!
    }

    /**
     * Creates all the DBs and users.
     */
    override fun bootstrap(container: GenericContainer<*>) {
        DriverManager.getConnection("jdbc:mysql://root:$rootPassword@localhost:${container.getMappedPort(3306)}").use { conn ->
            dbs.forEach { db ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("create user '${db.username}'@'%' identified by '${db.password}'")
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("create database ${db.dbName}")
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("grant all privileges on ${db.dbName}.* to '${db.username}'@'%'")
                }
            }
        }
    }

    internal fun findDb(dbName: String): DbConfig {
        val db = dbs.find { it.dbName == dbName }
        if (db == null) {
            throw IllegalStateException("No db named $dbName configured")
        }
        return db
    }

    internal data class DbConfig(val dbName: String, val username: String, val password: String)
}

/**
 * Gets a JDBC URI for use inside your services.  Like:
 * ```
 * withProductEngineeringService(KnownServices.DocumentApi, "2.0.16") {
 *     configYaml(
 *         """
 *             spring:
 *               config:
 *                 import: classpath:application.yml
 *             ---
 *             spring:
 *               datasource:
 *                 url: ${internalJdbcUrlFor("document_api")}
 *               liquibase:
 *                 url: ${internalJdbcUrlFor("document_api")}
 *                 enabled: true
 *         """.trimIndent()
 *     )
 * }
 * ```
 */
fun internalJdbcUrlFor(dbName: String): String {
    val db = MySQLServiceContext.instance.findDb(dbName)
    return "jdbc:mysql://${db.username}:${db.password}@${SupportingServices.MySql.containerName}:3306/$dbName?createDatabaseIfNotExist=true"
}

/**
 * A JDBC URI for outside your services, in your tests.  You can use it to connect any client to DB.  But, prefer [DomainTestContext.withDatabase]
 */
fun externalJdbcUrlFor(dbName: String): String {
    val db = MySQLServiceContext.instance.findDb(dbName)
    return "jdbc:mysql://${db.username}:${db.password}@localhost:$externalMySqlPort/$dbName?createDatabaseIfNotExist=true"
}

/**
 * The port of the MySQL service for use outside your services, in your tests.  Probably not very useful on its own. See [DomainTestContext.withDatabase].
 */
val externalMySqlPort: Int
    get() {
        val container = MySQLServiceContext.instance.container
        if (container?.isRunning() != true) {
            throw IllegalStateException("MySQL container not started")
        }
        return container.getMappedPort(3306)
    }

/**
 * Retrieves the usernmae for the given DB.  Probably not very useful on its own.
 */
fun usernameFor(dbName: String): String = MySQLServiceContext.instance.findDb(dbName).username

/**
 * Retrieves the password for the given DB.  Probably not very useful on its own.
 */
fun passwordFor(dbName: String): String = MySQLServiceContext.instance.findDb(dbName).password
