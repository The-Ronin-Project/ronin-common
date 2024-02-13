package com.projectronin.domaintest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DomainTestExtension::class)
class OpenEndpointTest {
    @Test
    fun `should be able to retrieve actuator without auth`() = domainTest {
        val body = request("document-api", "/actuator").execute { it.bodyString() }

        assertThat(body).contains("beans")
    }

    @Test
    fun `should load swagger ui`() = domainTest {
        val body = request("document-api", "/swagger-ui.html").execute { it.bodyString() }

        assertThat(body).contains("<title>Swagger UI</title>")
    }

    @Test
    fun `should be able to get the contract`() = domainTest {
        val body = request("document-api", "/v3/api-docs").execute { it.bodyString() }

        assertThat(body).contains("/api/v1/tenants/")
    }
}
