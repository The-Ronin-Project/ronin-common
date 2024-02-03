package com.projectronin.test.jwt

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.removeStub
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.ContentPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey

/**
 * Creates a minimal authorization server implementation inside WireMock.
 *
 * You must pass a valid JWKSet and a root URL that your auth server will reside at.  Note that
 * the issuerRootUri probably needs to be the URI to the wiremock server as seen by the code
 * that will be _validating_ tokens.  So, for example, if you are writing a controller test for your service,
 * for example with `@WebMvcTest`, you will want to use an issuer pointing to the wiremock port on localhost, like
 * `http://localhost:WIREMOCKPORT/fakeAuth0`.  If you are writing a test where the code _and_ wiremock will run
 * inside a docker network as named containers, you may need to use the container name:  `http://wiremock:WIREMOCKPORT/fakeAuth0`.
 *
 * Note you have to have
 * configured wiremock properly before you call this function, by starting the server (embedded or in
 * docker) and then configuring the test framework with the right port like:
 * ```
 * class SomeTest {
 *     companion object {
 *         private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
 *
 *         @BeforeAll
 *         @JvmStatic
 *         fun setup() {
 *             wireMockServer.start()
 *             configureFor(wireMockServer.port())
 *         }
 *
 *         @AfterAll
 *         @JvmStatic
 *         fun tearDown() {
 *             wireMockServer.stop()
 *         }
 *     }
 *
 * }
 * ```
 */
fun createMockAuthServer(
    jwks: JWKSet,
    issueRootUri: String,
    issuerPath: String = "",
): List<StubMapping> {
    val mappings = mutableListOf<StubMapping>()

    val issuer = """$issueRootUri$issuerPath"""

    // language=json
    val openidConfiguration = """
                {
                    "issuer": "$issuer",
                    "authorization_endpoint": "$issueRootUri$issuerPath/oauth2/authorize",
                    "token_endpoint": "$issueRootUri$issuerPath/oauth2/token",
                    "token_endpoint_auth_methods_supported": [
                        "client_secret_basic",
                        "client_secret_post",
                        "client_secret_jwt",
                        "private_key_jwt"
                    ], 
                    "jwks_uri": "$issueRootUri$issuerPath/oauth2/jwks",
                    "userinfo_endpoint": "$issueRootUri$issuerPath/userinfo",
                    "response_types_supported": [
                        "code"
                    ],
                    "grant_types_supported": [
                        "authorization_code",
                        "client_credentials",
                        "refresh_token"
                    ],
                    "revocation_endpoint": "$issueRootUri$issuerPath/oauth2/revoke",
                    "revocation_endpoint_auth_methods_supported": [
                        "client_secret_basic",
                        "client_secret_post",
                        "client_secret_jwt",
                        "private_key_jwt"
                    ],
                    "introspection_endpoint": "$issueRootUri$issuerPath/oauth2/introspect",
                    "introspection_endpoint_auth_methods_supported": [
                        "client_secret_basic",
                        "client_secret_post",
                        "client_secret_jwt",
                        "private_key_jwt"
                    ],
                    "subject_types_supported": [
                        "public"
                    ],
                    "id_token_signing_alg_values_supported": [
                        "RS256"
                    ],
                    "scopes_supported": [
                        "openid"
                    ]
                }
            """.trimIndent()

    mappings += stubFor(
        get(urlPathMatching("$issuerPath/oauth2/jwks"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(createJWKSForPublicDisplay(jwks))
            )
    )

    mappings += stubFor(
        get(urlPathMatching("$issuerPath/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(openidConfiguration)
            )
    )

    return mappings.toList()
}

/**
 * Creates a response to an M2M-style credentials exchange, useful for mocking M2M token retrieval.
 *
 * Note you have to have
 * configured wiremock properly before you call this function, by starting the server (embedded or in
 * docker) and then configuring the test framework with the right port like:
 * ```
 * class SomeTest {
 *     companion object {
 *         private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
 *
 *         @BeforeAll
 *         @JvmStatic
 *         fun setup() {
 *             wireMockServer.start()
 *             configureFor(wireMockServer.port())
 *         }
 *
 *         @AfterAll
 *         @JvmStatic
 *         fun tearDown() {
 *             wireMockServer.stop()
 *         }
 *     }
 *
 * }
 * ```
 */
fun createM2MTokenProvider(token: String, issuerPath: String = "", scope: List<String> = emptyList(), bodyMatcher: ContentPattern<*>? = null): StubMapping {
    val tokenBody = mapOf(
        "access_token" to token,
        "scope" to scope.joinToString(" "),
        "expires_in" to 86400,
        "token_type" to "Bearer"
    )

    return stubFor(
        WireMock.post(urlPathMatching("$issuerPath/oauth/token"))
            .run { bodyMatcher?.let { withRequestBody(it) } ?: this }
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(JwtAuthTestJackson.objectMapper.writeValueAsString(tokenBody))
            )
    )
}

/**
 * Configures a local wiremock server to handle JWT auth.  For example:
 *
 * In test setup somewhere:
 * ```
 * val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
 * val rsaKey = AuthKeyGenerator.generateRandomRsa()
 * ```
 * And in your test:
 * ```
 * withAuthWiremockServer(rsaKey, wireMockServer.baseUrl(), "/fakeAuth0") {
 *     val token = jwtAuthToken {
 *         withUserType(RoninUserType.RoninEmployee)
 *             .withScopes("admin:read", "admin:write", "tenant:delete")
 *     }
 *     // do some tests
 * }
 * ```
 *
 * Everything said for `createMockAuthServer()` above applies to the values you intend to use here,
 * as well as the initial wiremock setup.
 */
fun <T> withAuthWiremockServer(
    rsaKey: RSAKey,
    issueRootUri: String,
    issuerPath: String = "",
    block: WireMockServerContext.() -> T,
): T {
    return WireMockServerContext(rsaKey, issueRootUri, issuerPath).use { block(it) }
}

/**
 * Used internally by `withAuthWiremockServer()`
 */
class WireMockServerContext(val rsaKey: RSAKey, val issueRootUri: String, val issuerPath: String = "") : AutoCloseable {

    private val stubs = mutableListOf<StubMapping>()

    init {
        stubs += createMockAuthServer(createJWKS(rsaKey), issueRootUri, issuerPath)
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
     */
    fun jwtAuthToken(rsaKey: RSAKey = this.rsaKey, issuer: String = "${this.issueRootUri}$issuerPath", block: RoninWireMockAuthenticationContext.() -> Unit = {}): String {
        return com.projectronin.test.jwt.jwtAuthToken(rsaKey, issuer, block)
    }

    /**
     * Sets up another mock auth endpoint.  I don't know why you'd want to do this, but...
     */
    fun withAnotherSever(rsaKey: RSAKey, issueRootUri: String, issuerPath: String): WireMockServerContext {
        stubs += createMockAuthServer(createJWKS(rsaKey), issueRootUri, issuerPath)
        return this
    }

    override fun close() {
        stubs.forEach { removeStub(it) }
    }
}
