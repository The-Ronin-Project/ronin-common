package com.projectronin.domaintest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.nimbusds.jose.jwk.RSAKey
import com.projectronin.test.jwt.RoninTokenBuilderContext
import com.projectronin.test.jwt.generateRandomRsa
import mu.KLogger
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.EMPTY_REQUEST
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.intellij.lang.annotations.Language
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Used to perform a domain test.  E.g.:
 *
 * ```
 * @Test
 * fun `should do some stuff`() = domainTest {
 *    // do some tests
 * }
 * ```
 */
fun domainTest(block: DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

/**
 * Used to perform a domain test that needs to call coroutines.  E.g.:
 *
 * ```
 * @Test
 * fun `should do some stuff`() = runBlocking {
 *    coDomainTest {
 *       // do some tests
 *    }
 * }
 * ```
 */
suspend fun coDomainTest(block: suspend DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

/**
 * A context object, created by one of the functions above, that gives you a basic DSL for running domain or contract tests.  For real examples, look through the
 * tests in this module.  But basically:
 *
 * ```
 * @Test
 * fun `should do some stuff`() = domainTest {
 *     setSessionToken(
 *         jwtAuthToken {
 *             withScopes("admin:read")
 *         }
 *     )
 *     request(KnownServices.Auth, udpMappingsPath)
 *         .defaultToken()
 *         .execute {
 *             // verify the response here using normal AssertJ or Junit statements
 *         }
 * }
 * ```
 */
class DomainTestContext : AutoCloseable {

    val logger: KLogger = KotlinLogging.logger { }

    /**
     * Caches a token so that it can be re-used throughout the test.  Use in requests with [RequestContext.defaultToken].
     */
    private var sessionToken: String? = null

    /**
     * The [OkHttpClient] that will be used to make requests
     */
    private var _httpClient: OkHttpClient? = null

    /**
     * A simple [ObjectMapper] for working with JSON requests and responses.
     */
    private var _objectMapper: ObjectMapper? = null

    private var authData: AuthData = AuthContext.defaultAuthProvider

    private var defaultService: ServiceDef = KnownServices.Gateway

    /**
     * An internal reference to the auth service or mock RSA key.  Used by tokens created with [jwtAuthToken] by default.
     */
    val rsaKey: RSAKey
        get() = authData.rsaKey()(this)

    /**
     * Internal reference to the appropriate token issuer.  Used by tokens created with [jwtAuthToken] by default.
     */
    val issuer: String
        get() = authData.issuer()(this)

    /**
     * Gets cached (or creates new) [OkHttpClient] for use in requests.
     */
    val httpClient: OkHttpClient
        get() {
            if (_httpClient == null) {
                _httpClient = OkHttpClient.Builder()
                    .connectTimeout(60L, TimeUnit.SECONDS)
                    .readTimeout(60L, TimeUnit.SECONDS)
                    .build()
            }
            return _httpClient!!
        }

    /**
     * Gets cached (or creates new) [ObjectMapper] for any json encoding/decoding.
     */
    val objectMapper: ObjectMapper
        get() {
            if (_objectMapper == null) {
                _objectMapper = newMinimalObjectMapper()
            }
            return _objectMapper!!
        }

    /**
     * Allows you to globally override the default authentication configuration for _this instance of `domainTest` only
     */
    fun withAuthData(authData: AuthData) {
        this.authData = authData
    }

    fun withDefaultService(service: ServiceDef) {
        this.defaultService = service
    }

    /**
     * set the token for the session. It will get added to all subsequent [RequestContext.defaultToken] setups.
     */
    fun setSessionToken(token: String) {
        require(token.isNotBlank()) { "must specify a token" }
        sessionToken = token
    }

    /**
     * Clears the token for the session.  Subsequent calls to [RequestContext.defaultToken] will fail.
     */
    fun clearSessionToken() {
        sessionToken = null
    }

    /**
     * A utility builder for making a new [OkHttpClient] and writing it to [httpClient].
     */
    fun buildHttpClient(builder: OkHttpClient.Builder = httpClient.newBuilder(), block: OkHttpClient.Builder.() -> OkHttpClient.Builder) {
        _httpClient = block(builder).build()
    }

    /**
     * Clears the current [httpClient].
     */
    fun clearHttpClient() {
        _httpClient = null
    }

    /**
     * Initiate a request with a [ServiceDef].  Like:
     * ```
     * get(udpMappingsPath) {
     *     token(token)
     * }.execute(expectedHttpStatus = HttpURLConnection.HTTP_UNAUTHORIZED) {
     *     // we only care here that the response was OK
     * }
     * post(udpMappingsPath, someUdpMapping) {
     *     token(token)
     * }.execute(expectedHttpStatus = HttpURLConnection.HTTP_UNAUTHORIZED) {
     *     // we only care here that the response was OK
     * }
     * get("/foo/bar', service = KnownServices.Assets) {
     *     token(token)
     * }.execute(expectedHttpStatus = HttpURLConnection.HTTP_UNAUTHORIZED) {
     *     // we only care here that the response was OK
     * }
     * ```
     * Uses the [sessionToken] if set.
     */
    fun request(
        path: String,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.get(path, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    fun get(
        path: String,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.get(path, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    fun post(
        path: String,
        body: Any,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.post(path, body, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    fun put(
        path: String,
        body: Any,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.put(path, body, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    fun patch(
        path: String,
        body: Any,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.patch(path, body, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    fun delete(
        path: String,
        body: Any? = null,
        service: ServiceDef = defaultService,
        block: RequestContext.() -> Unit = {}
    ): RequestContext {
        val requestContext = RequestContext()
        requestContext.delete(path, body, service)
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    /**
     * Initiate a request without context, as below.  Use this method for POST, for calling things that aren't defined services,
     * etc.
     *
     * ```
     *  request {
     *      serviceGet("auth", udpMappingsPath)
     *      token(token)
     *  }.execute(expectedHttpStatus = HttpURLConnection.HTTP_UNAUTHORIZED) {
     *      // we only care here that the response was OK
     *  }
     * ```
     * Uses the [sessionToken] if set.
     */
    fun request(block: RequestContext.() -> Unit = {}): RequestContext {
        val requestContext = RequestContext()
        sessionToken?.let { requestContext.defaultToken() }
        block(requestContext)
        return requestContext
    }

    /**
     * Returns a JWT auth token.  See `AuthWireMockHelper.defaultRoninClaims()` for the defaults
     * that get set into it.  You can pass a block that customizes the code, e.g.:
     *
     * ```
     * val token = jwtAuthToken {
     *     withUserType(RoninUserType.RoninEmployee)
     *         .withScopes("admin:read", "admin:write", "tenant:delete")
     * }
     * ```
     *
     * Note that you will have to be running the auth service via [DomainTestSetupContext.withAuth] or a mock server
     * via [WireMockServiceContext.withOIDCSupport], and to put the correct issuer into your application YAML like:
     *
     * ```
     * ronin:
     *   auth:
     *     issuers:
     *       - ${authServiceIssuer()}
     *       - ${oidcIssuer()}
     * ```
     */
    fun jwtAuthToken(overrideKey: RSAKey? = null, block: RoninTokenBuilderContext.() -> Unit = {}): String {
        return com.projectronin.test.jwt.jwtAuthToken(overrideKey ?: rsaKey, issuer) {
            block(this)
        }
    }

    /**
     * Same as [jwtAuthToken] but won't validate because it's not signed with a key the server knows.  To produce 401's.
     */
    fun invalidJwtAuthToken(): String {
        return com.projectronin.test.jwt.jwtAuthToken(generateRandomRsa(), issuer)
    }

    /**
     * Sets/replaces the Authorization Bearer header on the request.  You probably don't really need to use this.  Prefer
     * [RequestContext.token] or [RequestContext.defaultToken].
     */
    fun Request.Builder.bearerAuthorization(token: String): Request.Builder {
        header("Authorization", "Bearer $token")
        return this
    }

    fun Any.asRequestBody(modifyJsonNode: ((JsonNode) -> Unit)? = null): RequestBody =
        when (this) {
            is RequestBody -> this
            else -> objectMapper.writeValueAsString(this).run {
                objectMapper.readTree(this)
                    .apply(modifyJsonNode ?: {})
                    .let { objectMapper.writeValueAsString(it) }
            }.toRequestBody("application/json".toMediaType())
        }

    /**
     * Convert a contract request class to a RequestBody.  Optionally you can specify a
     * modifyJsonNode block to remove or modify the actual request to test validation etc.
     */
    fun <T> requestBody(
        request: T,
        modifyJsonNode: (JsonNode) -> Unit = {}
    ): RequestBody = request?.asRequestBody(modifyJsonNode) ?: EMPTY_REQUEST

    /**
     * Returns the body as a string and fails if no body
     */
    fun Response.bodyString() = body?.string() ?: fail("no body")

    /**
     * returns the body as the specified type and fails if no body
     */
    inline fun <reified T> Response.readBodyValue(): T =
        this.body?.readValue<T>() ?: fail("no body")

    /**
     * Returns the body as the specified type
     */
    inline fun <reified T> ResponseBody?.readValue(): T =
        this?.string()?.let { objectMapper.readValue<T>(it) } ?: fail("no body")

    /**
     * Returns the body as a JsonNode and fails if no body
     */
    fun Response.readBodyTree(): JsonNode =
        this.body?.readTree() ?: fail("no body")

    /**
     * Returns the body as a JsonNode.
     */
    fun ResponseBody.readTree(): JsonNode =
        objectMapper.readTree(string())

    /**
     * Removes a field from an object and fails if it's not an object
     */
    fun JsonNode.removeObjectField(fieldName: String) {
        val objectNode = (this as? ObjectNode) ?: fail("not an object")
        objectNode.remove(fieldName)
    }

    /**
     * Gets a database connection from the mysql container.  Prefer [Database.getConnection].
     */
    fun getDatabaseConnection(dbName: String): Connection = DriverManager.getConnection(externalJdbcUrlFor(dbName))

    /**
     * Gets a database context for testing with a database.  For example:
     * ```
     * withDatabase("assets") {
     *     executeQuery("SELECT * FROM tenant t WHERE t.tenant_id = '$tenantId'") {
     *         assertThat(it.next()).isTrue()
     *     }
     * }
     * ```
     */
    fun withDatabase(dbName: String, block: Database.() -> Unit) {
        block(Database(dbName))
    }

    /**
     * Gets a database context for testing with a database, using a [DatabaseTables] instance.  For example:
     * ```
     * withDatabase(AssetsDatabaseTables) {
     *     executeQuery("SELECT * FROM tenant t WHERE t.tenant_id = '$tenantId'") {
     *         assertThat(it.next()).isTrue()
     *     }
     * }
     * ```
     */
    fun withDatabase(dtTables: DatabaseTables, block: Database.() -> Unit) {
        block(Database(dtTables))
    }

    inner class Database(val dbName: String) {

        constructor(databaseTables: DatabaseTables) : this(databaseTables.dbName) {
            this.databaseTables = databaseTables
        }

        private var databaseTables: DatabaseTables? = null

        /**
         * Gets a connection to the DB it was constructed with
         */
        fun getConnection(): Connection = getDatabaseConnection(dbName)

        /**
         * Executes a query against [getConnection]
         */
        fun <T> executeQuery(@Language("sql") sql: String, block: (ResultSet) -> T): T {
            getDatabaseConnection(dbName).use { conn ->
                conn.createStatement().use { stmt ->
                    return block(stmt.executeQuery(sql))
                }
            }
        }

        /**
         * Executes an update against [getConnection]
         */
        fun executeUpdate(@Language("sql") sql: String): Int =
            getDatabaseConnection(dbName).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(sql)
                }
            }

        /**
         * Creates a cleanup context.  For example
         *
         * ```
         * withDatabase(AssetsDatabaseTables) {
         *     cleanupWithDeleteBuilder { cleanup ->
         *
         *         cleanup += AssetsDatabaseTables.TENANT_TABLE.recordForId(tenantId)
         *
         *         // do something that would create the record that you will be deleting
         *         // do your assertions, etc
         *     }
         * }
         * ```
         */
        fun cleanupWithDeleteBuilder(
            builder: DeleteBuilder = DeleteBuilder(
                databaseTables ?: throw IllegalStateException("Must pass your own delete builder if you didn't construct this with a DatabaseTables instance")
            ),
            block: (DeleteBuilder) -> Unit
        ) {
            try {
                block(builder)
            } finally {
                getConnection().use { conn ->
                    conn.autoCommit = true
                    builder.build().forEach { sql ->
                        conn.createStatement().use { stmt ->
                            stmt.executeUpdate(sql)
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a block for you to do kafka stuff with, assuming kafka is running.  Mostly so you can produce messages:
     * ```
     * withKafka {
     *     producer(
     *         DocumentsServicesProvider.tenantTopic,
     *         RoninEventSerializer<TenantV1Schema>()
     *     ).use { producer ->
     *         producer.send(
     *             "ronin.tenant.tenant.create/${tenantId.value}",
     *             RoninEvent(
     *                 dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
     *                 source = "ronin-tenant",
     *                 type = "ronin.tenant.tenant.create",
     *                 tenantId = tenantId,
     *                 data = TenantV1Schema().apply {
     *                 }
     *             )
     *         )
     *     }
     * }
     * ```
     */
    fun withKafka(block: Kafka.() -> Unit) {
        block(Kafka())
    }

    /**
     * See [withKafka]
     */
    inner class Kafka {

        /**
         * Produces kafka messages.  See [withKafka]
         */
        fun <T> producer(
            topic: String,
            valueSerializer: Serializer<T>,
            keyPrefix: String = "",
            properties: Properties = Properties()
        ): TestKafkaProducer<T> {
            properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaExternalBootstrapServers
            properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.PLAINTEXT.name

            check(registeredTopics.contains(topic)) { "topic $topic not registered with the KafkaServiceContext" }

            val producer = KafkaProducer(
                properties,
                StringSerializer(),
                valueSerializer
            )

            return TestKafkaProducer(producer, topic, keyPrefix)
        }
    }

    /**
     * See [withKafka]
     */
    class TestKafkaProducer<T>(
        private val producer: KafkaProducer<String, T>,
        private val topic: String,
        private val keyPrefix: String
    ) : AutoCloseable {
        fun send(key: String, message: T) =
            producer.send(producerRecord(key, message)).get(30L, TimeUnit.SECONDS)

        private fun producerRecord(key: String, message: T) =
            ProducerRecord(topic, "$keyPrefix$key", message)

        override fun close() {
            runCatching {
                with(producer) {
                    flush()
                    close()
                }
            }
        }
    }

    /**
     * Used by [request] to chain a set of 302 redirects.  For example:
     *
     * ```
     * request()
     *    .get(externalWiremockUrl("/path/1"))
     *    .chainRedirects {
     *        assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/2"))
     *    }
     *    .then("On to the second path") {
     *        assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/3"))
     *    }
     *    .then("On to the third path", expectedStatus = HttpURLConnection.HTTP_OK) {
     *        assertThat(it.header("Content-Type")).isEqualTo("application/json")
     *        assertThat(it.body?.string()).isEqualTo("""{"ok": true}""")
     *    }
     * ```
     */
    inner class RedirectContext(requestContext: RequestContext, label: String, expectedStatus: Int, verifier: (Response) -> Unit) {

        private val response: Response = run {
            logger.info("Executing redirect chain entry to $label")
            requestContext.execute(expectedStatus) { response ->
                verifier(response)
                response
            }
        }

        /**
         * Chain another request.  You can expect something different, provide a specific label, modify the URL that will be used, and verify the response.
         */
        fun then(label: String? = null, expectedStatus: Int = HttpURLConnection.HTTP_MOVED_TEMP, urlProvider: (Response) -> URL = { it.redirectUrl }, verifier: (Response) -> Unit): RedirectContext {
            val url = urlProvider(response)
            return RedirectContext(
                requestContext = request().configure { url(url) },
                label = label ?: url.toString(),
                expectedStatus = expectedStatus,
                verifier = verifier
            )
        }
    }

    /**
     * Methods to execute a request built with one of the [request] methods.
     */
    interface RequestExecutor {
        /**
         * Execute the request, expecting a particular status and then executing a verifier block:
         * ```
         * request {
         *     serviceGet(KnownServices.Auth, udpMappingsPath)
         *     token(token)
         * }.execute(expectedHttpStatus = HttpURLConnection.HTTP_FORBIDDEN) {
         *     assertThat(it.header("Accept")).isEqualTo("foo")
         * }
         * ```
         */
        fun <T> execute(expectedHttpStatus: Int = HttpURLConnection.HTTP_OK, responseBlock: (Response) -> T): T

        /**
         * Execute a request that you expect to return a 400, using the returned object to verify the precise failure.  For example:
         * ```
         *  request()
         *     .gatewayPost(
         *         path = "/api/v1/tenants/apposnd/patients/apposnd-94xNAMvgsMWzJAh8vEhR9vSioXkGn5/assets",
         *         body = requestBody(
         *             AssetSchema().apply {
         *                 tenantId = "tenant"
         *                 patientId = "patient"
         *                 resourceType = "foo"
         *             }
         *         )
         *     )
         *     .executeBadRequest().verifyInvalidFieldValue("filename")
         * ```
         */
        fun executeBadRequest(): BadRequest

        /**
         * Execute this request, expecting a 302, and verifying the response.  Returns an object that allows you to check subsequent requests.  For example:
         * ```
         * request()
         *    .get(externalWiremockUrl("/path/1"))
         *    .chainRedirects {
         *        assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/2"))
         *    }
         *    .then("On to the second path") {
         *        assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/3"))
         *    }
         *    .then("On to the third path", expectedStatus = HttpURLConnection.HTTP_OK) {
         *        assertThat(it.header("Content-Type")).isEqualTo("application/json")
         *        assertThat(it.body?.string()).isEqualTo("""{"ok": true}""")
         *    }
         * ```
         */
        fun chainRedirects(label: String = "Initial request", expectedStatus: Int = HttpURLConnection.HTTP_MOVED_TEMP, verifier: (Response) -> Unit): RedirectContext
    }

    /**
     * Created by a call to one of the [request] methods.  See those for basic usage.
     */
    inner class RequestContext : RequestExecutor {
        private var builder: Request.Builder = Request.Builder()

        private fun determineUri(path: String, service: ServiceDef) = path.takeIf { it.startsWith("http") } ?: externalUriFor(service, path)

        /**
         * Configure this request as a GET call to a service by path, using a predefined ServiceDef, like [KnownServices.Assets]  If "path" is an url, will ignore service.
         */
        fun get(path: String, service: ServiceDef = defaultService): RequestContext {
            builder = builder.url(determineUri(path, service)).get()
            return this
        }

        /**
         * Configure this request as a POST call to a service by path, using a predefined ServiceDef, like [KnownServices.Assets].  If "path" is an url, will ignore service.
         */
        fun post(path: String, body: Any, service: ServiceDef = defaultService): RequestContext {
            builder = builder.url(determineUri(path, service)).post(body.asRequestBody())
            return this
        }

        /**
         * Configure this request as a DELETE call to a service by name and path.  If "path" is an url, will ignore service.
         */
        fun delete(path: String, requestBody: Any? = null, service: ServiceDef = defaultService) {
            builder = builder.url(determineUri(path, service)).delete(requestBody?.asRequestBody())
        }

        /**
         * Configure this request as a PUT call to a service by name and path.  If "path" is an url, will ignore service.
         */
        fun put(path: String, requestBody: Any, service: ServiceDef = defaultService) {
            builder = builder.url(determineUri(path, service)).put(requestBody.asRequestBody())
        }

        /**
         * Configure this request as a PATCH call to a service by name and path.  If "path" is an url, will ignore service.
         */
        fun patch(path: String, requestBody: Any, service: ServiceDef = defaultService) {
            builder = builder.url(determineUri(path, service)).patch(requestBody.asRequestBody())
        }

        /**
         * Configures this request to use the [sessionToken].  Will throw an exception if it's null.
         */
        fun defaultToken(): RequestContext {
            assert(sessionToken != null) { "No session token set" }
            builder = builder.bearerAuthorization(sessionToken!!)
            return this
        }

        /**
         * Configures this request to send no Authorization header.
         */
        fun clearToken(): RequestContext {
            builder = builder.removeHeader("Authorization")
            return this
        }

        /**
         * Configures this request to use the given token.
         */
        fun token(token: String): RequestContext {
            builder = builder.bearerAuthorization(token)
            return this
        }

        /**
         * Gives direct access to the [Request.Builder] object.  Can be used to send request
         * methods not in this code, change headers, etc.
         */
        fun configure(block: Request.Builder.() -> Request.Builder): RequestContext {
            builder = block(builder)
            return this
        }

        /**
         * Executes the request, checks the expected status, and executes a lambda with
         * the request's response object.
         */
        override fun <T> execute(expectedHttpStatus: Int, responseBlock: (Response) -> T): T {
            val request = builder.build()
            return httpClient.newCall(request).also {
                logger.info(request.toString())
            }.execute().use { response ->
                logger.info(response.toString())

                assertThat(response.code).isEqualTo(expectedHttpStatus)
                response.let(responseBlock)
            }
        }

        /**
         * Executes the request, assuming it will be a BAD REQUEST response.  Parses the returned JSON
         * and constructs a [BadRequest] object for further assertions.
         */
        override fun executeBadRequest(): BadRequest = execute(HttpURLConnection.HTTP_BAD_REQUEST) {
            it.readBodyTree().let { data ->
                assertThat(data["error"]?.textValue()).isEqualTo("Bad Request")
                BadRequest(
                    message = data["message"]?.textValue() ?: "",
                    detail = data["detail"]?.textValue() ?: ""
                )
            }
        }

        /**
         * Executes the request, assuming a 302 response, and returns a redirect context for further checks.
         */
        override fun chainRedirects(label: String, expectedStatus: Int, verifier: (Response) -> Unit): RedirectContext {
            return RedirectContext(this, label, expectedStatus, verifier)
        }
    }

    /**
     * A utility for testing 400 errors.  Used internally.  See [RequestContext.executeBadRequest]
     */
    data class BadRequest(
        val message: String,
        val detail: String
    ) {
        /**
         * Asserts that the response has a message indicating a missing field.
         */
        fun verifyMissingRequiredField(fieldName: String): BadRequest {
            assertThat(message).isEqualTo("Missing required field '$fieldName'")
            return this
        }

        /**
         * Asserts that the response has a Validation Failure and a list of details.
         */
        fun verifyValidationFailure(vararg detailContent: String): BadRequest {
            assertThat(message).isEqualTo("Validation failure")
            assertThat(detail).contains(detailContent.toList())
            return this
        }

        /**
         * Asserts the response has an invalid value message for a specific field.
         */
        fun verifyInvalidFieldValue(fieldName: String): BadRequest {
            assertThat(message).isEqualTo("Invalid value for field '$fieldName'")
            return this
        }
    }

    /**
     * Cleans up any resources.
     */
    override fun close() {
        // nothing right now
    }
}

/**
 * Utility to extract a URL from the response Location field.  Used by [DomainTestContext.RedirectContext]
 */
val Response.redirectUrl
    get() = headers["Location"]?.let { URL(it) } ?: fail("Location required in response")

/**
 * Utility to construct a simple object mapper.
 */
fun newMinimalObjectMapper(): ObjectMapper {
    val m = ObjectMapper()
    m.findAndRegisterModules()
    return m
}
