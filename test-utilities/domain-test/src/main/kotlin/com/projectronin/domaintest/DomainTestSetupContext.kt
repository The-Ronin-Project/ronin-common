package com.projectronin.domaintest

import mu.KLogger
import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

interface ServiceDef {
    val serviceName: String
    val imageName: String
    val gatewayVariables: List<String>
}

enum class KnownServices(
    override val serviceName: String,
    override val imageName: String,
    override val gatewayVariables: List<String> = emptyList()
) : ServiceDef {
    Gateway("api", "prodeng-api-gateway"),
    Auth("auth", "auth-service", listOf("SERVICES_AUTH_URI", "SERVICES_SEKI_URI")),
    Assets("assets", "assets-service", listOf("SERVICES_ASSETS_URI")),
    Capi("capi", "prodeng-clinician-api", listOf("SERVICES_CAPI_URI")),
    Cts("cts", "clinician-triage-api", listOf("SERVICES_CTS_URI")),
    Timeline("timeline", "prodeng-timeline-service", listOf("SERVICES_TIMELINE_URI")),
    DocumentApi("document-api", "document-api-service", listOf("SERVICES_DOCUMENT_API_URI")),
    DocumentData("document-data", "document-data-service"),
    Tenant("tenant", "tenant-service", listOf("SERVICES_TENANT_URI"))
}

interface SupportingService {
    val containerName: String
}

enum class SupportingServices(override val containerName: String) : SupportingService {
    MySql("mysql"),
    Kafka("kafka"),
    Wiremock("wiremock")
}

class DomainTestSetupContext internal constructor() {

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

    fun withSupportingService(service: SupportingService, context: DomainTestContainerContext) {
        services += service.containerName to ServiceInfo(
            service.containerName,
            context
        )
    }

    fun withMySQL(fn: MySQLServiceContext.() -> Unit = { }) {
        val context = MySQLServiceContext.createInstance(network, "root")
        fn(context)
        withSupportingService(SupportingServices.MySql, context)
    }

    fun withKafka(fn: KafkaServiceContext.() -> Unit = { }) {
        val context = KafkaServiceContext.createInstance(network)
        fn(context)
        withSupportingService(SupportingServices.Kafka, context)
    }

    fun withWireMock(fn: WireMockServiceContext.() -> Unit = {}) {
        val context = WireMockServiceContext.createInstance(network)
        fn(context)
        withSupportingService(SupportingServices.Wiremock, context)
    }

    fun withAuth(version: String) {
        withMySQL()
        withWireMock {
            withM2MSupport()
        }
        withProductEngineeringService(KnownServices.Auth, version) {
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
                      - ${oidcIssuer()}
                  auth0:
                    domain-url: ${internalOidcIssuer()}
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

    fun withGateway(version: String, block: ApiGatewayContext.() -> Unit = {}) {
        val ctx = ApiGatewayContext()
        block(ctx)
        withProductEngineeringService(KnownServices.Gateway, version) {
            withoutConfigYaml()
            extraConfiguration {
                ctx.serviceMap.forEach { mapping ->
                    withEnv(mapping.key, mapping.value)
                }
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

    fun withProductEngineeringService(service: ServiceDef, version: String, fn: ProductEngineeringServiceContext.() -> Unit) {
        withProductEngineeringService(service.serviceName, service.imageName, version, fn)
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

fun authServiceIssuer(): String = "http://${KnownServices.Auth.serviceName}:8080"

class ApiGatewayContext() {
    internal val serviceMap = mutableMapOf<String, String>()

    init {
        KnownServices.values().forEach(::withServiceMapping)
    }

    fun withServiceMapping(serviceDef: ServiceDef): ApiGatewayContext {
        serviceDef.gatewayVariables.forEach { varName ->
            serviceMap += varName to "http://${serviceDef.serviceName}:8080"
        }
        return this
    }
}
