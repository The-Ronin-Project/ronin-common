package com.projectronin.domaintest

import mu.KLogger
import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

/**
 * Used to identify a product engineering service, with associated API gateway and other setups.
 */
abstract class ServiceDef(
    val serviceName: String,
    val imageName: String,
    val gatewayVariables: List<String> = emptyList()
) : DomainTestContainer(serviceName)

/**
 * Known services.  Services that might be desired in many domain tests should be added here, especially
 * if they are part of the API gateway configurations.
 */
object KnownServices {
    val Gateway: ServiceDef = object : ServiceDef("api", "prodeng-api-gateway") {}
    val Auth: ServiceDef = object : ServiceDef("auth", "auth-service", listOf("SERVICES_AUTH_URI", "SERVICES_SEKI_URI")) {}
    val Assets: ServiceDef = object : ServiceDef("assets", "assets-service", listOf("SERVICES_ASSETS_URI")) {}
    val Capi: ServiceDef = object : ServiceDef("capi", "prodeng-clinician-api", listOf("SERVICES_CAPI_URI")) {}
    val Cts: ServiceDef = object : ServiceDef("cts", "clinician-triage-api", listOf("SERVICES_CTS_URI")) {}
    val Timeline: ServiceDef = object : ServiceDef("timeline", "prodeng-timeline-service", listOf("SERVICES_TIMELINE_URI")) {}
    val DocumentApi: ServiceDef = object : ServiceDef("document-api", "document-api-service", listOf("SERVICES_DOCUMENT_API_URI")) {}
    val DocumentData: ServiceDef = object : ServiceDef("document-data", "document-data-service") {}
    val Tenant: ServiceDef = object : ServiceDef("tenant", "tenant-service", listOf("SERVICES_TENANT_URI")) {}

    /**
     * Returns a set of all ServiceDef children of this object.
     */
    fun values(): Set<ServiceDef> {
        return KnownServices::class.declaredMemberProperties
            .filter { it.returnType.isSubtypeOf(ServiceDef::class.starProjectedType) }
            .map { field -> field.get(KnownServices)!! as ServiceDef }
            .toSet()
    }
}

/**
 * Represents a container run by testcontainers for the domain test.  You should have
 * an instance of this for every service you start.  See [KnownServices] or [SupportingServices]
 */
abstract class DomainTestContainer(val containerName: String) : Comparable<DomainTestContainer> {

    override fun compareTo(other: DomainTestContainer): Int = containerName.compareTo(other.containerName)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DomainTestContainer) return false

        if (containerName != other.containerName) return false

        return true
    }

    override fun hashCode(): Int {
        return containerName.hashCode()
    }
}

/**
 * Contains known supporting services.  E.g. MySql.
 */
object SupportingServices {
    val MySql: DomainTestContainer = object : DomainTestContainer("mysql") {}
    val Kafka: DomainTestContainer = object : DomainTestContainer("kafka") {}
    val Wiremock: DomainTestContainer = object : DomainTestContainer("wiremock") {}
}

/**
 * The run context for the tests.  You should interact with it through
 * your implementation of [DomainTestServicesProvider].  See there for
 * a brief example.
 */
class DomainTestSetupContext internal constructor() {

    private val logger: KLogger = KotlinLogging.logger { }

    /**
     * Filesystem-friendly unique run name, based on the local time of the run
     */
    private val testRunName: String = LocalDateTime.now().toString().replace("[^0-9-]".toRegex(), "-")

    /**
     * Tries to find the /build directory of your project and builds a `build/test-executions/YYYY-MM-DDTHH-MM-SS-SSSSS
     * directory to contain container logs and configurations.  Look there if you're trying to debug things.
     */
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

    /**
     * A map of container names to configured services that are part of this test run.
     */
    private val services = mutableMapOf<String, ServiceInfo>()

    /**
     * A docker network so all the containers can communicate with one another.
     */
    val network = Network.newNetwork()

    /**
     * Adds a supporting service, like MySql or Kafka, to the services that will be launched.  Generally,
     * you don't want to use this:  instead use the specific ones below.
     */
    fun withSupportingService(service: DomainTestContainer, context: DomainTestContainerContext) {
        services += service.containerName to ServiceInfo(
            service.containerName,
            context
        )
    }

    /**
     * Adds MySQL to the services to run.  For example:
     * ```
     * withMySQL {
     *     withDatabase("foo", "foouser", "p@ssword")
     * }
     * ```
     */
    fun withMySQL(fn: MySQLServiceContext.() -> Unit = { }) {
        val context = MySQLServiceContext.createInstance(network, "root")
        fn(context)
        withSupportingService(SupportingServices.MySql, context)
    }

    /**
     * Adds Kafka to teh services to run.  For example:
     * ```
     * withKafka {
     *     topics("topic1", "topic2")
     * }
     * ```
     */
    fun withKafka(fn: KafkaServiceContext.() -> Unit = { }) {
        val context = KafkaServiceContext.createInstance(network)
        fn(context)
        withSupportingService(SupportingServices.Kafka, context)
    }

    /**
     * Adds WireMock to the services to run.  For example
     * ```
     * withWireMock {
     *     withM2MSupport()
     * }
     * ```
     * Automatically configures the WireMock test framework to use right port.  But doesn't do _anything_ to reset.  If you use
     * `resetWiremock()` to reset before and after your tests, the OIDC and M2M endpoints will be restored.
     */
    fun withWireMock(fn: WireMockServiceContext.() -> Unit = {}) {
        val context = WireMockServiceContext.createInstance(network)
        fn(context)
        withSupportingService(SupportingServices.Wiremock, context)
    }

    /**
     * Starts the prodeng-auth-service as part of the run
     */
    fun withAuth(version: String) {
        withMySQL()
        withWireMock {
            withM2MSupport()
        }
        withProductEngineeringService(KnownServices.Auth, version) {
            dependsOnMySQL("auth")
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

    /**
     * Starts the API Gateway as part of the run.  Will point the gateway's services
     * at locally running services.  You don't need to add service mappings for [KnownServices].
     * ```
     * withGateway("1.0.25") {
     *     withServiceMapping(object : ServiceDef("new-service-not-in-KnownServices", "new-service", listOf("GATEWAY_SERVICE_NAME")) {})
     * }
     * ```
     */
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

    /**
     * Adds a Product Engineering service.  Specifically, any service that's based on ronin-blueprint should work here.
     * See [ProductEngineeringServiceContext], and look at DocumentsServicesProvider in the test for full examples.  Prefer
     * the version that takes a ServiceDef below.
     */
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

    /**
     * Adds a Product Engineering service.  Specifically, any service that's based on ronin-blueprint should work here.
     * See [ProductEngineeringServiceContext], and look at DocumentsServicesProvider in the test for full examples.
     */
    fun withProductEngineeringService(service: ServiceDef, version: String, fn: ProductEngineeringServiceContext.() -> Unit) {
        withProductEngineeringService(service.serviceName, service.imageName, version, fn)
    }

    /**
     * Starts all the services
     */
    internal fun start() {
        services.values.forEach(::startService)
    }

    /**
     * Starts a service, after first starting its dependencies.
     */
    private fun startService(service: ServiceInfo) {
        service.context.dependencies
            .forEach { dependency ->
                startService(services[dependency.containerName] ?: throw IllegalStateException("Service ${service.serviceName} depends on $dependency, which does not exist"))
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

    /**
     * Stops the services
     */
    internal fun stop() {
        services.values.forEach { service ->
            synchronized(service) {
                logger.info("Stopping ${service.serviceName}")
                service.container?.stop()
            }
        }
    }

    /**
     * Internal class used to keep track of services.
     */
    private class ServiceInfo(
        val serviceName: String,
        val context: DomainTestContainerContext
    ) {
        var container: GenericContainer<*>? = null
    }
}

/**
 * A helper function to get the issuer expected by the auth service.  For example:
 *
 */
fun authServiceIssuer(): String = "http://${KnownServices.Auth.serviceName}:8080"

/**
 * A context class for the api gateway.  Mostly allows setting up service mappings
 * for services that aren't already part of [KnownServices]. For example:
 * ```
 * withGateway("version") {
 *    withServiceMapping(MyNewService)
 * }
 */
class ApiGatewayContext {
    internal val serviceMap = mutableMapOf<String, String>()

    init {
        KnownServices.values().forEach(::withServiceMapping)
    }

    /**
     * See above.
     */
    fun withServiceMapping(serviceDef: ServiceDef): ApiGatewayContext {
        serviceDef.gatewayVariables.forEach { varName ->
            serviceMap += varName to "http://${serviceDef.serviceName}:8080"
        }
        return this
    }
}
