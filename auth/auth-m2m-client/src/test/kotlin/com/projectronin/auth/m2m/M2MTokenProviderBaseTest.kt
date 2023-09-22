package com.projectronin.auth.m2m

import com.projectronin.auth.token.RoninLoginProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class M2MTokenProviderBaseTest {
    @Test
    fun `should get a token from the endpoint`() {
        val provider = buildProvider { request ->
            assertThat(request.scope).isEmpty()

            success(if (request.audience == "baz") "FOO" else "BAR")
        }

        assertThat(provider.getToken("baz")).isEqualTo("FOO")
        assertThat(provider.getToken("qux")).isEqualTo("BAR")
    }

    @Test
    fun `should get a token from the endpoint with a different URL`() {
        val provider = buildProvider("auth") { request ->
            assertThat(request.scope).isEmpty()

            success(if (request.audience == "baz") "FOO" else "BAR")
        }

        assertThat(provider.getToken("baz")).isEqualTo("FOO")
        assertThat(provider.getToken("qux")).isEqualTo("BAR")
    }

    @Test
    fun `should pass on the scopes`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("super_admin")

            success("FOO")
        }

        assertThat(provider.getToken("baz", scopes = listOf("super_admin"))).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("impersonate_tenant:apposnd")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = null
                )
            )
        ).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant without adding additional scopes`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("impersonate_tenant:apposnd")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                scopes = listOf("impersonate_tenant:apposnd"),
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = null
                )
            )
        ).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant and provider`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("impersonate_tenant:apposnd impersonate_provider:jim")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = "jim",
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = null
                )
            )
        ).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant and provider with scope pre-specified`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("impersonate_provider:any impersonate_tenant:apposnd")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                scopes = listOf("impersonate_provider:any"),
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = "jim",
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = null
                )
            )
        ).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant and patient`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("super_admin impersonate_tenant:apposnd impersonate_patient:kim impersonate_patient:k")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                scopes = listOf("super_admin"),
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = "kim",
                    accessingExternalPatientId = "k"
                )
            )
        ).isEqualTo("FOO")
    }

    @Test
    fun `should get a token for a tenant and provider and patient`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEqualTo("super_admin impersonate_provider:jim impersonate_tenant:apposnd impersonate_patient:kim impersonate_patient:k")

            success("FOO")
        }

        assertThat(
            provider.getToken(
                audience = "baz",
                scopes = listOf("super_admin", "impersonate_provider:jim"),
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = "jim",
                    accessingPatientUdpId = "kim",
                    accessingExternalPatientId = "k"
                )
            )
        ).isEqualTo("FOO")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should get a the same token without re-request but will request again after expiration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler, name = "Default Dispatcher")
        var clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
        var callCount = 0
        var firstRequest = true

        val provider = buildProvider(clock = { clock }, coroutineDispatcher = dispatcher) { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEmpty()

            val accessToken = if (firstRequest) "FOO" else "BAZ"

            if (firstRequest) {
                firstRequest = false
            }

            success(accessToken)
        }

        var listenedForToken: TokenResponse? = null
        val listener = TokenListener { newToken ->
            listenedForToken = newToken
            callCount += 1
        }
        provider.addTokenListener("baz", listener = listener)

        assertThat(provider.getToken("baz")).isEqualTo("FOO")
        runCurrent()
        assertThat(listenedForToken!!.accessToken).isEqualTo("FOO")
        assertThat(callCount).isEqualTo(1)

        assertThat(provider.getToken("baz")).isEqualTo("FOO")
        runCurrent()
        assertThat(listenedForToken!!.accessToken).isEqualTo("FOO")
        assertThat(callCount).isEqualTo(1)

        clock = Clock.offset(clock, Duration.ofSeconds(86401))

        assertThat(provider.getToken("baz")).isEqualTo("BAZ")
        // verify that in fact we get the response before the coroutine runs
        assertThat(listenedForToken!!.accessToken).isEqualTo("FOO")
        assertThat(callCount).isEqualTo(1)

        // advance the context
        runCurrent()
        assertThat(listenedForToken!!.accessToken).isEqualTo("BAZ")
        assertThat(callCount).isEqualTo(2)

        provider.removeTokenListener("baz", listener = listener)

        clock = Clock.offset(clock, Duration.ofSeconds(86401))

        assertThat(provider.getToken("baz")).isEqualTo("BAZ")

        // because we removed it, it shouldn't get called again
        runCurrent()
        assertThat(listenedForToken!!.accessToken).isEqualTo("BAZ")
        assertThat(callCount).isEqualTo(2)
    }

    @Test
    fun `should throw an exception when it fails to get a token`() {
        val errorMessage = "Client has not been granted scopes: impersonate_tenant:ronin"

        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEmpty()

            Result.failure(M2MTokenException(errorMessage))
        }

        assertThatThrownBy { provider.getToken("baz") }
            .isInstanceOf(M2MTokenException::class.java)
            .hasMessage(errorMessage)
    }

    @Test
    fun `should fail if no tenant was specified`() {
        val provider = buildProvider { request ->
            assertThat(request.audience).isEqualTo("baz")
            assertThat(request.scope).isEmpty()

            success("FOO")
        }

        assertThatThrownBy {
            provider.getToken(
                audience = "baz",
                requestedProfile = RoninLoginProfile(
                    accessingTenantId = null,
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = "kim",
                    accessingExternalPatientId = null
                )
            )
        }.isInstanceOf(M2MImpersonationException::class.java)
            .hasMessage("If requesting a profile, accessingTenantId must be specified")
    }
}

private fun buildProvider(
    authPath: String = "oauth",
    clock: () -> Clock = { Clock.systemUTC() },
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    responseProvider: (TokenRequest) -> Result<TokenResponse>
) = TestM2MTokenProvider(
    authPath,
    clock,
    coroutineDispatcher,
    responseProvider = { request, providedAuthPath ->
        assertThat(providedAuthPath).isEqualTo(authPath)
        assertThat(request.clientId).isEqualTo("testClientId")
        assertThat(request.clientSecret).isEqualTo("testClientSecret")

        responseProvider(request)
    }
)

fun success(accessToken: String) =
    Result.success(
        TokenResponse(
            accessToken = accessToken,
            scope = "bar",
            expiresIn = 86400,
            tokenType = "Bearer"
        )
    )

private class TestM2MTokenProvider(
    authPath: String,
    clock: () -> Clock,
    coroutineDispatcher: CoroutineDispatcher,
    private val responseProvider: (TokenRequest, String) -> Result<TokenResponse>
) : M2MTokenProviderBase(
    clientId = "testClientId",
    clientSecret = "testClientSecret",
    authPath = authPath,
    clock = clock,
    coroutineDispatcher = coroutineDispatcher
) {
    override fun getNewToken(tokenRequest: TokenRequest, authPath: String): Result<TokenResponse> =
        responseProvider(tokenRequest, authPath)
}
