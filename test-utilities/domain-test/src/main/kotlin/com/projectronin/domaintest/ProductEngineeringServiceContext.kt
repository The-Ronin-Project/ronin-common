package com.projectronin.domaintest

import org.intellij.lang.annotations.Language
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Duration
import java.util.UUID

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
    private var attemptCoverage: Boolean = false
    private var coverageViable: Boolean = false
    private var enableDebugging: Boolean = false
    private var suspendIfDebuggingEnabled: Boolean = false
    private val internalCoverageAgentPath = "/coverage/agent.jar"
    private val internalCoverageOutputPath = "/coverage/output/jacoco"
    private val agentJarLocation: File by lazy {
        val tf = File.createTempFile("coverage-agent", ".jar")
        tf.deleteOnExit()
        tf
    }
    private val coverageDir: File by lazy {
        val f = File(testRunDirectory.absolutePath.replace("(.*/build).*".toRegex(), "$1")).resolve("jacoco")
        if (!f.exists()) {
            f.mkdirs()
        }
        f
    }

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

    fun withDebugging(suspend: Boolean = false) {
        enableDebugging = true
        suspendIfDebuggingEnabled = suspend
    }

    fun withCoverage() {
        attemptCoverage = true
    }

    private fun debuggingOptions(): List<String> {
        return if (enableDebugging) {
            listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=${if (suspendIfDebuggingEnabled) "y" else "n"},address=*:5005")
        } else {
            emptyList()
        }
    }

    private fun coverageOptions(): List<String> {
        return if (attemptCoverage) {
            val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
            val arguments = runtimeMxBean.inputArguments

            val javaAgentArgument = arguments.firstOrNull { it.matches("""-javaagent.*jacocoagent.jar.*""".toRegex()) }

            if (javaAgentArgument != null) {
                val jarPathPattern = "-javaagent:(.*?jacocoagent.jar)".toRegex()
                val jarPathExtractionPattern = ".*$jarPathPattern.*".toRegex()
                val sourceJarLocation = File(javaAgentArgument.replace(jarPathExtractionPattern, "$1"))

                sourceJarLocation.copyTo(agentJarLocation, overwrite = true)

                val newJavaAgentArgument = javaAgentArgument
                    .replace(jarPathPattern, "-javaagent:$internalCoverageAgentPath")
                    .replace("build/jacoco/.*?\\.exec".toRegex(), "$internalCoverageOutputPath/test-${UUID.randomUUID()}.exec")

                coverageViable = true

                listOf(newJavaAgentArgument)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override fun createContainer(): GenericContainer<*> {
        val configPair = configPairProvider()
        val serviceConfigFile: File = applicationRunDirectory.resolve("application.${configPair?.first ?: "yml"}")
        val serviceConfigContainerPath = "/domaintest/config.${configPair?.first ?: "yml"}"
        configPair?.let { serviceConfigFile.writeText(it.second) }

        val options = debuggingOptions() + coverageOptions()
        val ports = listOf(8080) + if (enableDebugging) listOf(5005) else emptyList()

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
            .run {
                options.takeIf { it.isNotEmpty() }?.let { withEnv("JDK_JAVA_OPTIONS", it.joinToString(" ")) } ?: this
            }
            .run {
                if (coverageViable) {
                    @Suppress("DEPRECATION")
                    withFileSystemBind(agentJarLocation.absolutePath, internalCoverageAgentPath)
                        .withFileSystemBind(coverageDir.absolutePath, internalCoverageOutputPath)
                } else {
                    this
                }
            }
            .withExposedPorts(*ports.toTypedArray())
            .waitingFor(LogMessageWaitStrategy().withRegEx(".*Started .* in .* seconds.*"))
            .withStartupTimeout(Duration.parse("PT5M"))
            .apply { extraConfig.fold(this) { container, cfg -> cfg(container) } }
    }

    override fun bootstrap(container: GenericContainer<*>) {
        // do nothing
        serviceMap += serviceName to container

        writeDebugConfigIfPossible(container)
    }

    private fun writeDebugConfigIfPossible(container: GenericContainer<*>) {
        if (enableDebugging) {
            fun findIdeaDir(dir: File): File? {
                val childDir = dir.resolve(".idea")
                return if (childDir.exists()) {
                    childDir
                } else {
                    val parentFile = dir.parentFile
                    if (parentFile != null) {
                        findIdeaDir(parentFile)
                    } else {
                        return null
                    }
                }
            }

            findIdeaDir(testRunDirectory)?.let { ideaDir ->
                val destDir = ideaDir.resolve("runConfigurations")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                destDir.resolve("$serviceName.xml").writeText(
                    // language=xml
                    """
                    <component name="ProjectRunConfigurationManager">
                      <configuration default="false" name="$serviceName" type="Remote">
                        <option name="USE_SOCKET_TRANSPORT" value="true" />
                        <option name="SERVER_MODE" value="false" />
                        <option name="SHMEM_ADDRESS" />
                        <option name="HOST" value="localhost" />
                        <option name="PORT" value="${container.getMappedPort(5005)}" />
                        <option name="AUTO_RESTART" value="false" />
                        <RunnerSettings RunnerId="Debug">
                          <option name="DEBUG_PORT" value="${container.getMappedPort(5005)}" />
                          <option name="LOCAL" value="false" />
                        </RunnerSettings>
                        <method v="2" />
                      </configuration>
                    </component>
                    """.trimIndent()
                )
            }
        }
    }
}

fun externalUriFor(serviceName: String): String =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.let { "http://localhost:${it.getMappedPort(8080)}" } ?: throw IllegalStateException("No started service named $serviceName")

fun externalUriFor(service: ServiceDef): String = externalUriFor(service.serviceName)

fun exposedServicePort(serviceName: String, port: Int): Int =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.getMappedPort(port) ?: throw IllegalStateException("No started service named $serviceName")

fun exposedServicePort(service: ServiceDef, port: Int): Int = exposedServicePort(service.serviceName, port)
