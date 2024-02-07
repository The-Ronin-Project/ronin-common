package com.projectronin.domaintest

import org.intellij.lang.annotations.Language
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

class ProductEngineeringServiceContext internal constructor(
    private val imageName: String,
    private val version: String,
    private val testRunDirectory: File,
    private val serviceName: String,
    private val network: Network
) : DomainTestContainerContext {

    companion object {
        internal val serviceMap = mutableMapOf<String, GenericContainer<*>>()
    }

    private val applicationRunDirectory = testRunDirectory.resolve(serviceName).also { it.mkdirs() }
    private val serviceYamlFile = applicationRunDirectory.resolve("application.yaml")
    private val _dependencies = mutableSetOf<String>()
    private var extraConfig: GenericContainer<*>.() -> GenericContainer<*> = { this }

    override val dependencies: Set<String>
        get() = _dependencies.toSet()

    // language=yaml
    private var _configYaml: String =
        """
        spring:
          config:
            import: classpath:application.yml
        ---
        spring:
          datasource:
            url:  "jdbc:mysql://${imageName.replace("[^a-zA-Z]+".toRegex(), "_")}/auth?createDatabaseIfNotExist=true"
            username: root
            password: root
          liquibase:
            enabled: true
        logging:
          level:
            root: ERROR
            com.projectronin: INFO
        """.trimIndent()

    fun configYaml(
        @Language("yaml") yaml: String
    ) {
        _configYaml = yaml
    }

    fun extraConfiguration(block: GenericContainer<*>.() -> GenericContainer<*>) {
        extraConfig = block
    }

    fun dependsOnMySQL() {
        _dependencies += DomainTestSetupContext.mysqlContainerName
    }

    fun dependsOnMySQLDatabase(dbName: String, username: String = dbName, password: String = dbName) {
        _dependencies += DomainTestSetupContext.mysqlContainerName
        MySQLServiceContext.instance.withDatabase(dbName, username, password)
    }

    fun dependsOnKafka(vararg topic: String) {
        _dependencies += DomainTestSetupContext.kafkaContainerName
        KafkaServiceContext.instance.topics(*topic)
    }

    fun dependsOnWireMock() {
        _dependencies += DomainTestSetupContext.wiremockContainerName
    }

    private fun writeConfigYaml() {
        serviceYamlFile.writeText(_configYaml)
    }

    override fun createContainer(): GenericContainer<*> {
        writeConfigYaml()
        return GenericContainer(DockerImageName.parse("docker-proxy.devops.projectronin.io/$imageName:$version"))
            .withNetwork(network)
            .withNetworkAliases(serviceName)
            .withEnv("SPRING_CONFIG_LOCATION", "/domaintest/config.yml")
            .withEnv("SPRING_PROFILES_ACTIVE", "local,domaintest")
            .withCopyToContainer(Transferable.of(_configYaml), "/domaintest/config.yml")
            .withExposedPorts(8080)
            .waitingFor(LogMessageWaitStrategy().withRegEx(".*Started .* in .* seconds.*"))
            .withStartupTimeout(Duration.parse("PT5M"))
            .apply { extraConfig(this) }
    }

    override fun bootstrap(container: GenericContainer<*>) {
        // do nothing
        serviceMap += serviceName to container
    }
}

fun externalUriFor(serviceName: String): String =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.let { "http://localhost:${it.getMappedPort(8080)}" } ?: throw IllegalStateException("No started service named $serviceName")

fun exposedServicePort(serviceName: String, port: Int): Int =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.getMappedPort(port) ?: throw IllegalStateException("No started service named $serviceName")
