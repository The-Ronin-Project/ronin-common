package com.projectronin.test.jwt

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.listAllStubMappings
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SimpleSecurityContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class AuthWiremockHelpersTest {

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun setup() {
            wireMockServer.start()
            configureFor(wireMockServer.port())
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            wireMockServer.stop()
        }
    }

    @Test
    fun `should set up mock oauth servers`() {
        val key1 = generateRandomRsa()
        val key2 = generateRandomRsa()

        val issuer2Path = "/fakeAuth0"
        val issuer2 = "${wireMockServer.baseUrl()}$issuer2Path"

        withAuthWiremockServer(key1, wireMockServer.baseUrl()) {
            withAnotherSever(key2, wireMockServer.baseUrl(), issuer2Path)
            val m2mToken = jwtAuthToken(key2, issuer2)
            withM2MTokenProvider(
                m2mToken,
                issuer2Path,
                listOf("foo", "bar")
            )

            val mainJwks = loadFromOIDC("${wireMockServer.baseUrl()}/.well-known/openid-configuration")
            assertThat(mainJwks).isNotNull
            assertThat(mainJwks[0].keyID).isEqualTo(key1.keyID)

            val m2mJwks = loadFromOIDC("${wireMockServer.baseUrl()}$issuer2Path/.well-known/openid-configuration")
            assertThat(m2mJwks).isNotNull
            assertThat(m2mJwks[0].keyID).isEqualTo(key2.keyID)

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${wireMockServer.baseUrl()}$issuer2Path/oauth/token"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()

            val m2mTokenResponse = JwtAuthTestJackson.objectMapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body())
            assertThat(m2mTokenResponse["access_token"].asText()).isEqualTo(m2mToken)
            assertThat(m2mTokenResponse["scope"].asText()).isEqualTo("foo bar")
            assertThat(m2mTokenResponse["expires_in"].asLong()).isEqualTo(86400L)
        }

        assertThat(listAllStubMappings().mappings).isEmpty()
    }

    private fun loadFromOIDC(oidcUrl: String): List<JWK> {
        val url = URL(JwtAuthTestJackson.objectMapper.readTree(URL(oidcUrl))["jwks_uri"].asText())
        val selector = JWKSelector(
            JWKMatcher.Builder()
                .keyType(KeyType.RSA)
                .build()
        )
        val ctx: SecurityContext = SimpleSecurityContext()
        val jwkSource = RemoteJWKSet<SecurityContext>(url)
        return jwkSource[selector, ctx]
    }
}
