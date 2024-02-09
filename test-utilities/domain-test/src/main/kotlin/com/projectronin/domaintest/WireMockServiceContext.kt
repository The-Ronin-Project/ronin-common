@file:Suppress("HttpUrlsUsage")

package com.projectronin.domaintest

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.projectronin.test.jwt.createJWKSForPublicDisplay
import com.projectronin.test.jwt.generateRandomRsa
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.wiremock.integrations.testcontainers.WireMockContainer

class WireMockServiceContext private constructor(private val network: Network) : DomainTestContainerContext {

    companion object {
        internal fun createInstance(network: Network): WireMockServiceContext {
            if (_instance == null) {
                _instance = WireMockServiceContext(network)
            }
            return _instance!!
        }

        private var _instance: WireMockServiceContext? = null
        internal val instance: WireMockServiceContext
            get() = _instance ?: throw IllegalStateException("No Wiremock service has been configured")
    }

    private var _container: WireMockContainer? = null
    private val container: WireMockContainer
        get() = _container ?: throw IllegalStateException("Wiremock service has not been started")

    private val arbitraryWiremockSetups = mutableListOf<() -> Unit>()

    internal val rsaKey = generateRandomRsa()

    private var m2mEnabled: Boolean = false
    private var oidcEnabled: Boolean = false

    init {
        _instance = this
    }

    fun oidcIssuerPath(): String = "/auth0"

    fun oidcIssuer(): String = "http://wiremock:8080${oidcIssuerPath()}"

    fun generateToken(rsaKey: RSAKey, claimSetCustomizer: JWTClaimsSet.Builder.() -> JWTClaimsSet.Builder = { this }): String =
        com.projectronin.test.jwt.generateToken(rsaKey, oidcIssuer(), claimSetCustomizer)

    fun withOIDCSupport() {
        if (!oidcEnabled) {
            oidcEnabled = true
            val jwks = JWKSet(listOf(rsaKey))
            val issuerPath = oidcIssuerPath()
            val issuer = oidcIssuer()
            arbitraryWiremockSetups += {
                // language=json
                val openidConfiguration = """
                {
                    "issuer": "$issuer",
                    "authorization_endpoint": "$issuer/oauth2/authorize",
                    "token_endpoint": "$issuer/oauth2/token",
                    "token_endpoint_auth_methods_supported": [
                        "client_secret_basic",
                        "client_secret_post",
                        "client_secret_jwt",
                        "private_key_jwt"
                    ], 
                    "jwks_uri": "$issuer/oauth2/jwks",
                    "userinfo_endpoint": "$issuer/userinfo",
                    "response_types_supported": [
                        "code"
                    ],
                    "grant_types_supported": [
                        "authorization_code",
                        "client_credentials",
                        "refresh_token"
                    ],
                    "revocation_endpoint": "$issuer/oauth2/revoke",
                    "revocation_endpoint_auth_methods_supported": [
                        "client_secret_basic",
                        "client_secret_post",
                        "client_secret_jwt",
                        "private_key_jwt"
                    ],
                    "introspection_endpoint": "$issuer/oauth2/introspect",
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

                stubFor(
                    get(urlPathMatching("$issuerPath/oauth2/jwks"))
                        .willReturn(
                            aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(createJWKSForPublicDisplay(jwks))
                        )
                )

                stubFor(
                    get(urlPathMatching("$issuerPath/.well-known/openid-configuration"))
                        .willReturn(
                            aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(openidConfiguration)
                        )
                )
            }
        }
    }

    fun withM2MSupport(vararg scope: String) {
        if (!m2mEnabled) {
            m2mEnabled = true
            withOIDCSupport()
            arbitraryWiremockSetups += {
                val issuerPath = oidcIssuerPath()

                val tokenBody = mapOf(
                    "access_token" to generateToken(rsaKey) {
                        if (scope.isNotEmpty()) {
                            claim("scope", scope.joinToString(","))
                        } else {
                            this
                        }
                    },
                    "scope" to scope.joinToString(" "),
                    "expires_in" to 86400,
                    "token_type" to "Bearer"
                )

                stubFor(
                    post(urlPathMatching("$issuerPath/oauth/token"))
                        .willReturn(
                            aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(newMinimalObjectMapper().writeValueAsString(tokenBody))
                        )
                )
            }
        }
    }

    override fun createContainer(): GenericContainer<*> {
        if (_container == null) {
            _container = WireMockContainer("wiremock/wiremock:2.35.0")
                .withNetwork(network)
                .withNetworkAliases(SupportingServices.Wiremock.containerName)
                .withCliArg("--verbose")
                .withCliArg("--local-response-templating")
        }
        return container
    }

    override fun bootstrap(container: GenericContainer<*>) {
        WireMock.configureFor(port)
        arbitraryWiremockSetups.forEach { it() }
    }

    internal fun reset() {
        WireMock.resetToDefault()
        arbitraryWiremockSetups.forEach { it() }
    }

    internal val port: Int
        get() = container.getMappedPort(8080)
}

fun internalWiremockUrl(path: String): String = "http://wiremock:8080$path"

fun externalWiremockUrl(path: String): String = "http://localhost:${WireMockServiceContext.instance.port}$path"

val externalWiremockPort: Int
    get() = WireMockServiceContext.instance.port

fun externalOidcIssuer(): String = "http://localhost:${WireMockServiceContext.instance.port}${WireMockServiceContext.instance.oidcIssuerPath()}"

fun internalOidcIssuer(): String = "http://wiremock:8080${WireMockServiceContext.instance.oidcIssuerPath()}"

fun oidcIssuer(): String = WireMockServiceContext.instance.oidcIssuer()

fun resetWiremock() {
    WireMockServiceContext.instance.reset()
}
