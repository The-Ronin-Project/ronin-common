package com.projectronin.auth

import com.projectronin.auth.token.RoninAuthenticationSchemeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoninAuthenticationSchemeTypeTest {
    @Test
    fun `forValue works with known type strings`() {
        mapOf(
            "SMART_ON_FHIR" to RoninAuthenticationSchemeType.SmartOnFhir,
            "MDA_TOKEN" to RoninAuthenticationSchemeType.MDAToken,
            "AUTH0_M2M" to RoninAuthenticationSchemeType.Auth0M2M,
            "AUTH0_GOOGLE_OAUTH" to RoninAuthenticationSchemeType.Auth0GoogleOauth,
            "AUTH0_USERNAME_PASSWORD" to RoninAuthenticationSchemeType.Auth0UsernamePassword,
            "AUTH0_OTP" to RoninAuthenticationSchemeType.Auth0OTP
        ).forEach { (knownTypeString, userType) ->
            assertThat(RoninAuthenticationSchemeType.forValue(knownTypeString)).isEqualTo(userType)
        }
    }

    @Test
    fun `forValue works with unknown type strings`() {
        listOf(
            "GARBAGE",
            "unknown",
            "foo",
            "bar"
        ).forEach { unknownTypeString ->
            assertThat(RoninAuthenticationSchemeType.forValue(unknownTypeString))
                .isEqualTo(RoninAuthenticationSchemeType.Unknown(unknownTypeString))
        }
    }
}
