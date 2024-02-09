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
    private val _dependencies = mutableSetOf<String>()
    private var extraConfig = mutableListOf<GenericContainer<*>.() -> GenericContainer<*>>({ this })
    private val activeSpringProfiles = mutableListOf("local", "domaintest")

    override val dependencies: Set<String>
        get() = _dependencies.toSet()

    private var configPairProvider: () -> Pair<String, String>? = {
        Pair(
            "yml",
            // language=yaml
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
        )
    }

    fun configYaml(
        @Language("yaml") yaml: String
    ) {
        configPairProvider = { Pair("yml", yaml) }
    }

    @Deprecated("Use configYaml instead")
    fun configProperties(
        @Language("properties") properties: String
    ) {
        configPairProvider = { Pair("properties", properties) }
    }

    fun configYamlProvider(
        provider: () -> String
    ) {
        configPairProvider = { Pair("yml", provider()) }
    }

    @Deprecated("Use configYaml instead")
    fun configPropertiesProvider(
        provider: () -> String
    ) {
        configPairProvider = { Pair("properties", provider()) }
    }

    fun withoutConfigYaml() {
        configPairProvider = { null }
    }

    fun extraConfiguration(block: GenericContainer<*>.() -> GenericContainer<*>) {
        extraConfig += block
    }

    fun dependsOnMySQL() {
        _dependencies += SupportingServices.MySql.containerName
    }

    fun dependsOnMySQLDatabase(dbName: String, username: String = dbName, password: String = dbName) {
        dependsOnMySQL()
        MySQLServiceContext.instance.withDatabase(dbName, username, password)
    }

    fun dependsOnKafka(vararg topic: String) {
        _dependencies += SupportingServices.Kafka.containerName
        KafkaServiceContext.instance.topics(*topic)
    }

    fun dependsOnWireMock() {
        _dependencies += SupportingServices.Wiremock.containerName
    }

    fun withAdditionalActiveSpringProfiles(vararg profile: String) {
        activeSpringProfiles += profile
    }

    fun withActiveSpringProfiles(vararg profile: String) {
        activeSpringProfiles.clear()
        activeSpringProfiles += profile
    }

    override fun createContainer(): GenericContainer<*> {
        val configPair = configPairProvider()
        val serviceConfigFile: File = applicationRunDirectory.resolve("application.${configPair?.first ?: "yml"}")
        val serviceConfigContainerPath: String = "/domaintest/config.${configPair?.first ?: "yml"}"
        configPair?.let { serviceConfigFile.writeText(it.second) }
        return GenericContainer(DockerImageName.parse("docker-proxy.devops.projectronin.io/$imageName:$version"))
            .withNetwork(network)
            .withNetworkAliases(serviceName)
            .withEnv("SPRING_PROFILES_ACTIVE", activeSpringProfiles.joinToString(","))
            .run {
                configPair?.let { cfg ->
                    withEnv("SPRING_CONFIG_LOCATION", serviceConfigContainerPath)
                        .withCopyToContainer(Transferable.of(cfg.second), serviceConfigContainerPath)
                } ?: this
            }
            .withExposedPorts(8080)
            .waitingFor(LogMessageWaitStrategy().withRegEx(".*Started .* in .* seconds.*"))
            .withStartupTimeout(Duration.parse("PT5M"))
            .apply { extraConfig.fold(this) { container, cfg -> cfg(container) } }
    }

    override fun bootstrap(container: GenericContainer<*>) {
        // do nothing
        serviceMap += serviceName to container
    }
}

fun externalUriFor(serviceName: String): String =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.let { "http://localhost:${it.getMappedPort(8080)}" } ?: throw IllegalStateException("No started service named $serviceName")

fun externalUriFor(service: ServiceDef): String = externalUriFor(service.serviceName)

fun exposedServicePort(serviceName: String, port: Int): Int =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.getMappedPort(port) ?: throw IllegalStateException("No started service named $serviceName")

fun exposedServicePort(service: ServiceDef, port: Int): Int = exposedServicePort(service.serviceName, port)
