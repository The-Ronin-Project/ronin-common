package com.projectronin.domaintest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DomainTestExtension::class)
class OpenEndpointTest {
    @Test
    fun `should be able to retrieve actuator without auth`() = domainTest {
        val body = request("/actuator", service = KnownServices.DocumentApi).execute { it.bodyString() }

        assertThat(body).contains("beans")
    }

    @Test
    fun `should load swagger ui`() = domainTest {
        val body = request("/swagger-ui.html", service = KnownServices.DocumentApi).execute { it.bodyString() }

        assertThat(body).contains("<title>Swagger UI</title>")
    }

    @Test
    fun `should be able to get the contract`() = domainTest {
        val body = request("/v3/api-docs", service = KnownServices.DocumentApi).execute { it.bodyString() }

        assertThat(body).contains("/api/v1/tenants/")
    }
}
