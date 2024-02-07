package com.projectronin.domaintest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.nimbusds.jose.jwk.RSAKey
import com.projectronin.test.jwt.RoninWireMockAuthenticationContext
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
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Properties
import java.util.concurrent.TimeUnit

fun domainTest(block: DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

suspend fun coDomainTest(block: suspend DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

class DomainTestContext : AutoCloseable {
    companion object;

    val logger: KLogger = KotlinLogging.logger { }

    private var sessionToken: String? = null

    private var _httpClient: OkHttpClient? = null

    private var _objectMapper: ObjectMapper? = null

    val authServiceName = "auth"

    val authServiceRsaKey: RSAKey by lazy {

        // this forces auth to instantiate the jwks, which is lazily created
        request {
            serviceGet(authServiceName, "/oauth2/jwks")
        }.execute { }

        val clientConfig = ClientConfig()
        clientConfig.clusterName = "spring-session-cluster"
        clientConfig.setProperty("hazelcast.logging.type", "slf4j")
        clientConfig.networkConfig.addAddress("localhost:${exposedServicePort(authServiceName, 5701)}")

        val client = HazelcastClient.newHazelcastClient(clientConfig)

        val key: RSAKey = client.getReplicatedMap<String, RSAKey>("rsaKey")["key"]!!

        client.shutdown()

        key
    }

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

    val objectMapper: ObjectMapper
        get() {
            if (_objectMapper == null) {
                _objectMapper = newMinimalObjectMapper()
            }
            return _objectMapper!!
        }

    /**
     * set the token for the session. It will get added to all subsequent buildRequest calls
     */
    fun setSessionToken(token: String) {
        require(token.isNotBlank()) { "must specify a token" }
        sessionToken = token
    }

    /**
     * clears the token for the session
     */
    fun clearSessionToken() {
        sessionToken = null
    }

    fun buildHttpClient(builder: OkHttpClient.Builder = httpClient.newBuilder(), block: OkHttpClient.Builder.() -> OkHttpClient.Builder) {
        _httpClient = block(builder).build()
    }

    fun clearHttpClient() {
        _httpClient = null
    }

    fun request(serviceName: String? = null, path: String = "", block: RequestContext.() -> Unit = {}): RequestContext {
        val requestContext = RequestContext(serviceName, path)
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
     * Note that you will have to be running wiremock (include `ContractTestWireMockService()` in your contract test provider),
     * and configure your service in application-test.yml or application-test.properties to include the wiremock service
     * as an issuer.  E.g.:
     *
     * ```
     * ronin:
     *   auth:
     *     issuers:
     *       - http://127.0.0.1:{{wireMockPort}}
     * ```
     */
    fun jwtAuthToken(block: RoninWireMockAuthenticationContext.() -> Unit = {}): String {
        return com.projectronin.test.jwt.jwtAuthToken(authServiceRsaKey, authServiceIssuer()) {
            block(this)
        }
    }

    fun invalidJwtAuthToken(): String {
        return com.projectronin.test.jwt.jwtAuthToken(generateRandomRsa(), authServiceIssuer())
    }

    /**
     *  Sets/replaces the Authorization Bearer header on the request.
     */
    fun Request.Builder.bearerAuthorization(token: String): Request.Builder {
        header("Authorization", "Bearer $token")
        return this
    }

    /**
     * convert a contract request class to a RequestBody.  Optionally you can specify a
     * modifyJsonNode block to remove or modify the actual request to test validation etc.
     */
    fun <T> requestBody(
        request: T,
        modifyJsonNode: (JsonNode) -> Unit = {}
    ): RequestBody =
        objectMapper.writeValueAsString(request).run {
            objectMapper.readTree(this)
                .apply(modifyJsonNode)
                .let { objectMapper.writeValueAsString(it) }
        }.toRequestBody("application/json".toMediaType())

    /**
     * returns the body as a string and fails if no body
     */
    fun Response.bodyString() = body?.string() ?: fail("no body")

    /**
     * returns the body as the specified type and fails if no body
     */
    inline fun <reified T> Response.readBodyValue(): T =
        this.body?.readValue<T>() ?: fail("no body")

    /**
     * returns the body as the specified type
     */
    inline fun <reified T> ResponseBody?.readValue(): T =
        this?.string()?.let { objectMapper.readValue<T>(it) } ?: fail("no body")

    /**
     * returns the body as a JsonNode and fails if no body
     */
    fun Response.readBodyTree(): JsonNode =
        this.body?.readTree() ?: fail("no body")

    /**
     * returns the body as a JsonNode
     */
    fun ResponseBody.readTree(): JsonNode =
        objectMapper.readTree(string())

    /**
     * removes a field from an object and fails if it's not an object
     */
    fun JsonNode.removeObjectField(fieldName: String) {
        val objectNode = (this as? ObjectNode) ?: fail("not an object")
        objectNode.remove(fieldName)
    }

    /**
     * gets a database connection from the mysql container to clean up database records as needed
     */
    fun getDatabaseConnection(dbName: String): Connection = DriverManager.getConnection(externalJdbcUrlFor(dbName))

    data class BadRequest(
        val message: String,
        val detail: String
    ) {
        fun verifyMissingRequiredField(fieldName: String): BadRequest {
            assertThat(message).isEqualTo("Missing required field '$fieldName'")
            return this
        }

        fun verifyValidationFailure(vararg detailContent: String): BadRequest {
            assertThat(message).isEqualTo("Validation failure")
            assertThat(detail).contains(detailContent.toList())
            return this
        }

        fun verifyInvalidFieldValue(fieldName: String): BadRequest {
            assertThat(message).isEqualTo("Invalid value for field '$fieldName'")
            return this
        }
    }

    fun withDatabase(dbName: String, block: Database.() -> Unit) {
        block(Database(dbName))
    }

    fun withDatabase(dtTables: DatabaseTables, block: Database.() -> Unit) {
        block(Database(dtTables))
    }

    fun withKafka(block: Kafka.() -> Unit) {
        block(Kafka())
    }

    inner class Database(val dbName: String) {

        constructor(databaseTables: DatabaseTables) : this(databaseTables.dbName) {
            this.databaseTables = databaseTables
        }

        private var databaseTables: DatabaseTables? = null

        fun getConnection(): Connection = getDatabaseConnection(dbName)

        fun <T> executeQuery(sql: String, block: (ResultSet) -> T): T {
            getDatabaseConnection(dbName).use { conn ->
                conn.createStatement().use { stmt ->
                    return block(stmt.executeQuery(sql))
                }
            }
        }

        fun executeUpdate(sql: String): Int =
            getDatabaseConnection(dbName).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(sql)
                }
            }

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

    inner class Kafka {
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

    inner class RedirectContext(requestContext: RequestContext, label: String, expectedStatus: Int, verifier: (Response) -> Unit) {

        private val response: Response = run {
            logger.info("Executing redirect chain entry to $label")
            requestContext.execute(expectedStatus) { response ->
                verifier(response)
                response
            }
        }

        fun then(label: String? = null, expectedStatus: Int = HttpURLConnection.HTTP_MOVED_TEMP, verifier: (Response) -> Unit): RedirectContext {
            return RedirectContext(
                requestContext = request().configure { url(response.redirectUrl) },
                label = label ?: response.redirectUrl.toString(),
                expectedStatus = expectedStatus,
                verifier = verifier
            )
        }
    }

    interface RequestExecutor {
        fun <T> execute(expectedHttpStatus: Int = HttpURLConnection.HTTP_OK, responseBlock: (Response) -> T): T
        fun executeBadRequest(): BadRequest
        fun chainRedirects(label: String = "Initial request", expectedStatus: Int = HttpURLConnection.HTTP_MOVED_TEMP, verifier: (Response) -> Unit): RedirectContext
    }

    inner class RequestContext(serviceName: String? = null, path: String = "") : RequestExecutor {
        var builder: Request.Builder = Request.Builder()

        init {
            if (serviceName != null) {
                serviceGet(serviceName, path)
            }
        }

        fun serviceGet(serviceName: String, path: String = ""): RequestContext {
            get("${externalUriFor(serviceName)}$path")
            return this
        }

        fun get(url: String): RequestContext {
            builder = builder
                .url(url)
                .get()
            return this
        }

        fun servicePost(serviceName: String, path: String = "", body: RequestBody): RequestContext {
            post("${externalUriFor(serviceName)}$path", body)
            return this
        }

        fun post(url: String, body: RequestBody): RequestContext {
            builder = builder
                .url(url)
                .post(body)
            return this
        }

        fun defaultToken(): RequestContext {
            assert(sessionToken != null) { "No session token set" }
            builder = builder.bearerAuthorization(sessionToken!!)
            return this
        }

        fun token(token: String): RequestContext {
            builder = builder.bearerAuthorization(token)
            return this
        }

        fun configure(block: Request.Builder.() -> Request.Builder): RequestContext {
            builder = block(builder)
            return this
        }

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

        override fun executeBadRequest(): BadRequest = execute(HttpURLConnection.HTTP_BAD_REQUEST) {
            it.readBodyTree().let { data ->
                assertThat(data["error"]?.textValue()).isEqualTo("Bad Request")
                BadRequest(
                    message = data["message"]?.textValue() ?: "",
                    detail = data["detail"]?.textValue() ?: ""
                )
            }
        }

        override fun chainRedirects(label: String, expectedStatus: Int, verifier: (Response) -> Unit): RedirectContext {
            return RedirectContext(this, label, expectedStatus, verifier)
        }
    }

    override fun close() {
        // nothing right now
    }
}

val Response.redirectUrl
    get() = headers["Location"]?.let { URL(it) } ?: fail("Location required in response")

fun newMinimalObjectMapper(): ObjectMapper {
    val m = ObjectMapper()
    m.findAndRegisterModules()
    return m
}
