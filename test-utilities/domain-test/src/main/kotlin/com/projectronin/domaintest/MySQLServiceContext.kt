package com.projectronin.domaintest

import com.projectronin.database.helpers.MysqlVersionHelper
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import java.sql.DriverManager

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

    fun withDatabase(dbName: String, username: String, password: String) {
        dbs += DbConfig(dbName, username, password)
    }

    override fun createContainer(): GenericContainer<*> {
        if (container == null) {
            container = MySQLContainer(MysqlVersionHelper.MYSQL_VERSION_OCI)
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword(rootPassword)
                .withNetwork(network)
                .withNetworkAliases(DomainTestSetupContext.mysqlContainerName)
        }
        return container!!
    }

    override fun bootstrap(container: GenericContainer<*>) {
        // nothing to do
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

fun internalJdbcUrlFor(dbName: String): String {
    val db = MySQLServiceContext.instance.findDb(dbName)
    return "jdbc:mysql://${db.username}:${db.password}@${DomainTestSetupContext.mysqlContainerName}:3306/$dbName?createDatabaseIfNotExist=true"
}

fun externalJdbcUrlFor(dbName: String): String {
    val db = MySQLServiceContext.instance.findDb(dbName)
    val container = MySQLServiceContext.instance.container
    if (container?.isRunning() != true) {
        throw IllegalStateException("MySQL container not started")
    }
    return "jdbc:mysql://${db.username}:${db.password}@localhost:${container.getMappedPort(3306)}/$dbName?createDatabaseIfNotExist=true"
}

fun usernameFor(dbName: String): String = MySQLServiceContext.instance.findDb(dbName).username
fun passwordFor(dbName: String): String = MySQLServiceContext.instance.findDb(dbName).password
