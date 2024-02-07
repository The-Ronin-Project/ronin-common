package com.projectronin.domaintest

import mu.KLogger
import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

class DomainTestSetupContext internal constructor() {

    companion object {
        val mysqlContainerName = "mysql"
        val kafkaContainerName = "kafka"
        val wiremockContainerName = "wiremock"
        val authServiceName = "auth"
    }

    private val logger: KLogger = KotlinLogging.logger { }
    private val testRunName: String = LocalDateTime.now().toString().replace("[^0-9-]".toRegex(), "-")
    private val testRunDirectory: File by lazy {
        val userDir = File(System.getProperty("user.dir"))
        val expectedBuildDir = userDir.resolve("build")
        val parentDirToUse = if (expectedBuildDir.exists()) {
            expectedBuildDir
        } else {
            val tempDir = Files.createTempDirectory("domain-test-run").toFile()
            tempDir.deleteOnExit()
            tempDir
        }
        val dir = parentDirToUse.resolve("test-executions/$testRunName")
        dir.mkdirs()
        logger.info { "Test will be executed from ${dir.absolutePath}" }
        dir
    }
    private val services = mutableMapOf<String, ServiceInfo>()

    private val network = Network.newNetwork()

    fun withMySQL(fn: MySQLServiceContext.() -> Unit = { }) {
        val context = MySQLServiceContext.createInstance(network, "root")
        fn(context)
        services += mysqlContainerName to ServiceInfo(
            mysqlContainerName,
            context
        )
    }

    fun withKafka(fn: KafkaServiceContext.() -> Unit = { }) {
        val context = KafkaServiceContext.createInstance(network)
        fn(context)
        services += kafkaContainerName to ServiceInfo(
            kafkaContainerName,
            context
        )
    }

    fun withWireMock(fn: WireMockServiceContext.() -> Unit = {}) {
        val context = WireMockServiceContext.createInstance(network)
        fn(context)
        services += wiremockContainerName to ServiceInfo(
            wiremockContainerName,
            context
        )
    }

    fun withAuth(version: String) {
        withMySQL()
        withWireMock {
            withM2MSupport()
        }
        withProductEngineeringService(authServiceName, "auth-service", version) {
            dependsOnMySQLDatabase("auth")
            dependsOnWireMock()
            configYaml(
                """
                spring:
                  config:
                    import: classpath:application.yml
                ---
                spring:
                  datasource:
                    url: ${internalJdbcUrlFor("auth")}
                  liquibase:
                    enabled: true
                ronin:
                  auth-server:
                    aidbox-url: "https://qa.project-ronin.aidbox.app"
                    aidbox-client-id: "seki"
                    aidbox-client-secret: "foo"
                    kusari-base-url: "https://dev.projectronin.io/kusari"
                    kusari-secret: "foo"
                    enable-mda-login-simulation: false
                    dashboard-redirect-url: "{baseScheme}://{baseHost}{basePort}/api/hello/ronin"
                  auth:
                    issuers:
                      - ${authServiceIssuer()}
                      - ${auth0Issuer()}
                  auth0:
                    domain-url: ${internalAuth0Uri()}
                    m2m-client-id: "foo"
                    m2m-client-secret: "bar"
                    use-auth0-user-management: false
                    management-domain-audience: "https://ronin-dev.auth0.com/api/v2/"
                    management-domain-url: "https://ronin-dev.auth0.com/"
                  kafka:
                    bootstrap-servers: ""
                    security-protocol: PLAINTEXT
                """.trimIndent()
            )
            extraConfiguration {
                addExposedPorts(5701)
                this
            }
        }
    }

    fun withProductEngineeringService(serviceName: String, imageName: String, version: String, fn: ProductEngineeringServiceContext.() -> Unit) {
        val info = services.getOrPut(
            serviceName
        ) {
            ServiceInfo(
                serviceName,
                ProductEngineeringServiceContext(
                    imageName = imageName,
                    version = version,
                    testRunDirectory = testRunDirectory,
                    serviceName = serviceName,
                    network = network
                )
            )
        }
        fn(info.context as ProductEngineeringServiceContext)
    }

    internal fun start() {
        services.values.forEach(::startService)
    }

    private fun startService(service: ServiceInfo) {
        service.context.dependencies
            .forEach { dependency ->
                startService(services[dependency] ?: throw IllegalStateException("Service ${service.serviceName} depends on $dependency, which does not exist"))
            }

        synchronized(service) {
            if (service.container == null) {
                logger.info("Starting ${service.serviceName}")
                val outputDir = testRunDirectory.resolve(service.serviceName)
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val outFile = outputDir.resolve("stdout.txt")
                val errFile = outputDir.resolve("stderr.txt")
                val container = service.context.createContainer()
                    .withLogConsumer { frame ->
                        when (frame.type) {
                            OutputFrame.OutputType.STDOUT -> outFile.appendBytes(frame.bytes)
                            OutputFrame.OutputType.END -> outFile.appendText("Output ended")
                            else -> errFile.appendBytes(frame.bytes)
                        }
                    }
                container.start()
                service.container = container
                service.context.bootstrap(container)
            }
        }
    }

    internal fun stop() {
        services.values.forEach { service ->
            synchronized(service) {
                logger.info("Stopping ${service.serviceName}")
                service.container?.stop()
            }
        }
    }

    private class ServiceInfo(
        val serviceName: String,
        val context: DomainTestContainerContext
    ) {
        var container: GenericContainer<*>? = null
    }
}

fun authServiceIssuer(): String = "http://${DomainTestSetupContext.authServiceName}:8080"
