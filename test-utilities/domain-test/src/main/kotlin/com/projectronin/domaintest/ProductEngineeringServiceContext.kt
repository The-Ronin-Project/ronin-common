package com.projectronin.domaintest

import mu.KLogger
import mu.KotlinLogging
import org.intellij.lang.annotations.Language
import org.jacoco.core.tools.ExecDumpClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Duration

/**
 * Context for a single PE service.  See [DomainTestSetupContext.withProductEngineeringService].  Specific useful methods are described here.
 */
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

    private val logger: KLogger = KotlinLogging.logger { }

    private val applicationRunDirectory = testRunDirectory.resolve(serviceName).also { it.mkdirs() }
    private val _dependencies = mutableSetOf<DomainTestContainer>()
    private var extraConfig = mutableListOf<GenericContainer<*>.() -> GenericContainer<*>>({ this })
    private val activeSpringProfiles = mutableListOf("local", "domaintest")
    private var attemptCoverage: Boolean = false
    private var coverageViable: Boolean = false
    private var enableDebugging: Boolean = false
    private var suspendIfDebuggingEnabled: Boolean = false
    private val internalCoverageAgentPath = "/coverage/agent.jar"
    private val internalCoverageOutputPath = "/coverage/output/jacoco"
    private val agentJarLocation: File by lazy {
        val tf = File.createTempFile("coverage-agent", ".jar", testRunDirectory)
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

    override val dependencies: Set<DomainTestContainer>
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

    /**
     * Provide the contents of the `application.yml` file that the service will bootstrap with.  It's probably useful to use a dynamic string rather
     * than the contents of a file here, because no replacement/manipulation is done by this method.  A full example might be like the below.  Note
     * all the inclusions of different service locations, topic names, etc.  Note also that this string is provided eagerly, so if you need for some
     * reason to access a value that isn't known until after other services start, that will fail here.  Use [configYamlProvider] if that's what you need.
     *
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
     *             ronin:
     *               auth:
     *                 issuers:
     *                   - ${authServiceIssuer()}
     *                   - ${oidcIssuer()}
     *               product:
     *                 document-api:
     *                   topic-document-events: "$documentEventsTopic"
     *                   topic-dlq: "$documentsDlqTopic"
     *                   topic-tenants: "$tenantTopic"
     *                   default-page-limit: 2
     *                   registry-uuid: "765b49c5-dff6-4fd9-9809-4c03fd9beb3a"
     *                   registry-version: 4
     *               kafka:
     *                 bootstrap-servers: $kafkaInternalBootstrapServers
     *                 security-protocol: PLAINTEXT
     *         """.trimIndent()
     *     )
     * }
     * ```
     */
    fun configYaml(
        @Language("yaml") yaml: String
    ) {
        configPairProvider = { Pair("yml", yaml) }
    }

    /**
     * Provide the contents of the `application.properties` file that the service will bootstrap with  Please use the YAML ones.  They're better..  It's probably useful to use a dynamic string rather
     * than the contents of a file here, because no replacement/manipulation is done by this method.  A full example might be like the below.  Note
     * all the inclusions of different service locations, topic names, etc.  Note also that this string is provided eagerly, so if you need for some
     * reason to access a value that isn't known until after other services start, that will fail here.  Use [configPropertiesProvider] if that's what you need.
     *
     * ```
     * withProductEngineeringService(KnownServices.DocumentApi, "2.0.16") {
     *     configProperties(
     *         """
     *             spring.datasource.url=${internalJdbcUrlFor("document_api")}
     *             spring.liquibase.url=${internalJdbcUrlFor("document_api")}
     *             spring.liquibase.enabled=true
     *             ronin.auth.issuers=${authServiceIssuer()},${oidcIssuer()}
     *             ronin.product.document-api.topic-document-events=$documentEventsTopic
     *             ronin.product.document-api.topic-dlq=$documentsDlqTopic
     *             ronin.product.document-api.topic-tenants=$tenantTopic
     *             ronin.product.document-api.default-page-limit=2
     *             ronin.product.document-api.registry-uuid=765b49c5-dff6-4fd9-9809-4c03fd9beb3a
     *             ronin.product.document-api.registry-version=4
     *             ronin.kafka.bootstrap-servers=$kafkaInternalBootstrapServers
     *             ronin.kafka.security-protocol=PLAINTEXT
     *         """.trimIndent()
     *     )
     * }
     * ```
     */
    @Deprecated("Use configYaml instead")
    fun configProperties(
        @Language("properties") properties: String
    ) {
        configPairProvider = { Pair("properties", properties) }
    }

    /**
     * Provide the contents of the `application.yml` file that the service will bootstrap with.  It's probably useful to use a dynamic string rather
     * than the contents of a file here, because no replacement/manipulation is done by this method.  A full example might be like the below.  Note
     * all the inclusions of different service locations, topic names, etc. This is provided lazily, so referring to ports and URLs that are produced
     * after dependent services are started (generally the externally available ones) is possible.  Be aware, though, that this service is running
     * in a container, so it can't _access_ other services at `localhost`
     *
     * ```
     * withProductEngineeringService(KnownServices.DocumentApi, "2.0.16") {
     *     configYamlProvider {
     *         // language=yaml
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
     *             ronin:
     *               auth:
     *                 issuers:
     *                   - ${authServiceIssuer()}
     *                   - ${oidcIssuer()}
     *               product:
     *                 document-api:
     *                   topic-document-events: "$documentEventsTopic"
     *                   topic-dlq: "$documentsDlqTopic"
     *                   topic-tenants: "$tenantTopic"
     *                   default-page-limit: 2
     *                   registry-uuid: "765b49c5-dff6-4fd9-9809-4c03fd9beb3a"
     *                   registry-version: 4
     *               kafka:
     *                 bootstrap-servers: $kafkaInternalBootstrapServers
     *                 security-protocol: PLAINTEXT
     *         """.trimIndent()
     *     }
     * }
     * ```
     */
    fun configYamlProvider(
        provider: () -> String
    ) {
        configPairProvider = { Pair("yml", provider()) }
    }

    /**
     * Provide the contents of the `application.properties` file that the service will bootstrap with  Please use the YAML ones.  They're better..  It's probably useful to use a dynamic string rather
     * than the contents of a file here, because no replacement/manipulation is done by this method.  A full example might be like the below.  Note
     * all the inclusions of different service locations, topic names, etc.  This is provided lazily, so referring to ports and URLs that are produced
     * after dependent services are started (generally the externally available ones) is possible.  Be aware, though, that this service is running
     * in a container, so it can't _access_ other services at `localhost`
     *
     * ```
     * withProductEngineeringService(KnownServices.DocumentApi, "2.0.16") {
     *     configPropertiesProvider {
     *         // language=properties
     *         """
     *             spring.datasource.url=${internalJdbcUrlFor("document_api")}
     *             spring.liquibase.url=${internalJdbcUrlFor("document_api")}
     *             spring.liquibase.enabled=true
     *             ronin.auth.issuers=${authServiceIssuer()},${oidcIssuer()}
     *             ronin.product.document-api.topic-document-events=$documentEventsTopic
     *             ronin.product.document-api.topic-dlq=$documentsDlqTopic
     *             ronin.product.document-api.topic-tenants=$tenantTopic
     *             ronin.product.document-api.default-page-limit=2
     *             ronin.product.document-api.registry-uuid=765b49c5-dff6-4fd9-9809-4c03fd9beb3a
     *             ronin.product.document-api.registry-version=4
     *             ronin.kafka.bootstrap-servers=$kafkaInternalBootstrapServers
     *             ronin.kafka.security-protocol=PLAINTEXT
     *         """.trimIndent()
     *     }
     * }
     * ```
     */
    @Deprecated("Use configYaml instead")
    fun configPropertiesProvider(
        provider: () -> String
    ) {
        configPairProvider = { Pair("properties", provider()) }
    }

    /**
     * Removes any configuration, including the default one.  You probably don't need this, but there might be a case where the jar-included one
     * is enough.  (Removes both yaml or properties, really, whichever is currently configured).
     */
    fun withoutConfigYaml() {
        configPairProvider = { null }
    }

    /**
     * Adds a lambda that can operate on the actual container instance.  This is useful if you need to (for instance) map additional configs or files
     * into the container, set environment variables, etc.
     */
    fun extraConfiguration(block: GenericContainer<*>.() -> GenericContainer<*>) {
        extraConfig += block
    }

    /**
     * Declares that this service depends on MySQL being started first.  You need this to make sure that that MySQL starts before your service.
     * Also you can supply a DB name here, which will cause MySQL to create that DB on startup.  It will throw an exception if this is a duplicate DB or USER.
     * Fails if mysql not previously defined with [DomainTestSetupContext.withMySQL]
     */
    fun dependsOnMySQL(dbName: String? = null, username: String? = null, password: String? = null) {
        dependsOnSupportingService(SupportingServices.MySql)
        dbName?.let {
            MySQLServiceContext.instance.withDatabase(it, username ?: it, password ?: it)
        }
    }

    /**
     * Declares that this service depends on kafka.  Will also create the given topics on kafka start.
     * Fails if kafka not previously defined with [DomainTestSetupContext.withKafka]
     */
    fun dependsOnKafka(vararg topic: String) {
        dependsOnSupportingService(SupportingServices.Kafka)
        KafkaServiceContext.instance.topics(*topic)
    }

    /**
     * Declares that this service depends on WireMock.
     * Fails if wiremock not previously defined with [DomainTestSetupContext.withWireMock]
     */
    fun dependsOnWireMock() {
        dependsOnSupportingService(SupportingServices.Wiremock)
    }

    /**
     * Declares that this service depends on the given supporting service
     * Fails if wiremock not previously defined with [DomainTestSetupContext.withSupportingService]
     */
    fun dependsOnSupportingService(service: DomainTestContainer) {
        _dependencies += service
    }

    /**
     * Adds a list of spring profiles to the default (which is `[local,domaintest]`)
     */
    fun withAdditionalActiveSpringProfiles(vararg profile: String) {
        activeSpringProfiles += profile
    }

    /**
     * Overrides the default spring profiles with the given ones.
     */
    fun withActiveSpringProfiles(vararg profile: String) {
        activeSpringProfiles.clear()
        activeSpringProfiles += profile
    }

    /**
     * Enables debugging of the container.  This exposes a port mapped to `5005` and adds the agentlib arguments for debugging.
     * You'll need to get the port from `docker ps` or `exposedServicePort(Service, 5005)` to actually attach.  And there will be no
     * available source code unless this test is inside the service project itself.  Also this will, when the service is started, write
     * a run configuration to `<project root>/.idea/runConfigurations/service-name.xml`, which idea _should_ pick up as an external
     * debugging configuration.  Be aware that the port will change every run, so make sure you refresh that file before debugging.
     *
     * If you ask for suspend, the service will hang on startup and you will have to attach a debugger before it will start.  Failing to start
     * will fail the test suite.
     */
    fun withDebugging(suspend: Boolean = false) {
        enableDebugging = true
        suspendIfDebuggingEnabled = suspend
    }

    /**
     * Attempt to use the service itself to provide coverage data for jacoco.
     */
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
                    .replace("destfile=.*\\.exec,?".toRegex(), "")
                    .replace("output=file", "output=tcpserver,address=*,port=6300")

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
        val ports = listOf(8080) + (if (enableDebugging) listOf(5005) else emptyList()) + (if (coverageViable) listOf(6300) else emptyList())

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

    override fun teardown(container: GenericContainer<*>) {
        if (coverageViable && container.isRunning()) {
            runCatching {
                val client = ExecDumpClient()
                client.dump("localhost", container.getMappedPort(6300)).save(coverageDir.resolve("test-$serviceName.exec"), false)
            }
                .onFailure {
                    logger.error(it) { "Failure to dump coverage." }
                }
        }
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

/**
 * Get the external (in the tests, not other services) URI for the given service.
 */
fun externalUriFor(serviceName: String, path: String = ""): String =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.let { "http://localhost:${it.getMappedPort(8080)}$path" } ?: throw IllegalStateException("No started service named $serviceName")

/**
 * Get the external (in the tests, not other services) URI for the given service, using a [ServiceDef]
 */
fun externalUriFor(service: ServiceDef, path: String = ""): String = externalUriFor(service.serviceName, path)

/**
 * Get the external (in the tests, not other services) port that maps to the given port for the service
 */
fun exposedServicePort(serviceName: String, port: Int): Int =
    ProductEngineeringServiceContext.serviceMap[serviceName]?.getMappedPort(port) ?: throw IllegalStateException("No started service named $serviceName")

/**
 * Get the external (in the tests, not other services) port that maps to the given port for the service using a [ServiceDef]
 */
fun exposedServicePort(service: ServiceDef, port: Int): Int = exposedServicePort(service.serviceName, port)
