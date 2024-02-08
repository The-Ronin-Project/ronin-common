package com.projectronin.domaintest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DomainTestExtension::class)
class ActuatorTest {

    @Test
    fun `should be able to retrieve actuator without auth`() = domainTest {
        val serviceInfo = request(serviceName = "auth", path = "/actuator/info").execute { response ->
            response.readBodyTree().get("serviceInfo")
        }

        assertThat(serviceInfo).isNotNull()
        assertThat(serviceInfo.isObject).isTrue()
        assertThat(serviceInfo["version"]?.textValue()).isNotBlank()
        assertThat(serviceInfo["lastTag"]?.textValue()).isNotBlank()
        assertThat(serviceInfo["commitDistance"]?.isInt).isTrue()
        assertThat(serviceInfo["gitHashFull"]?.textValue()).isNotBlank()
        assertThat(serviceInfo["branchName"]?.textValue()).isNotBlank()
        assertThat(serviceInfo["dirty"]?.isBoolean).isTrue()
    }
}
