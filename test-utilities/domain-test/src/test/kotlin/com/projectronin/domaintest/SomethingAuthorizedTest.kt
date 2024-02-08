package com.projectronin.domaintest

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.HttpURLConnection

@ExtendWith(DomainTestExtension::class)
class SomethingAuthorizedTest {

    private val udpMappingsPath = "/api/v1/udp-mappings?tenantId=apposnd"

    @Test
    fun `should able to make an authorized call with a token`() = domainTest {
        val token = jwtAuthToken {
            withScopes("admin:read")
        }
        request {
            serviceGet(KnownServices.Auth, udpMappingsPath)
            token(token)
        }.execute {
            // we only care here that the response was OK
        }
    }

    @Test
    fun `should get forbidden without the right scopes`() = domainTest {
        val token = jwtAuthToken()
        request {
            serviceGet(KnownServices.Auth, udpMappingsPath)
            token(token)
        }.execute(expectedHttpStatus = HttpURLConnection.HTTP_FORBIDDEN) {
            // we only care here that the response was OK
        }
    }

    @Test
    fun `should fail with a bad token`() = domainTest {
        val token = invalidJwtAuthToken()
        request {
            serviceGet(KnownServices.Auth.serviceName, udpMappingsPath)
            token(token)
        }.execute(expectedHttpStatus = HttpURLConnection.HTTP_UNAUTHORIZED) {
            // we only care here that the response was OK
        }
    }

    @Test
    fun `should be ok with default token`() = domainTest {
        setSessionToken(
            jwtAuthToken {
                withScopes("admin:read")
            }
        )
        request(KnownServices.Auth, udpMappingsPath)
            .defaultToken()
            .execute {
                // we only care here that the response was OK
            }
    }

    @Test
    fun `should fail if we clear it`() = domainTest {
        setSessionToken(
            jwtAuthToken {
                withScopes("admin:read")
            }
        )
        clearSessionToken()
        assertThatThrownBy { request(KnownServices.Auth, udpMappingsPath).defaultToken() }
            .isInstanceOf(AssertionError::class.java)
    }
}
