package com.projectronin.auth

import com.projectronin.auth.token.RoninUserType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoninUserTypeTest {
    @Test
    fun `forValue works with known type strings`() {
        mapOf(
            "PROVIDER" to RoninUserType.Provider,
            "PATIENT" to RoninUserType.Patient,
            "SERVICE" to RoninUserType.Service,
            "INTEGRATION_TEST" to RoninUserType.IntegrationTest,
            "RONIN_EMPLOYEE" to RoninUserType.RoninEmployee
        ).forEach { (knownTypeString, userType) ->
            assertThat(RoninUserType.forValue(knownTypeString)).isEqualTo(userType)
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
            assertThat(RoninUserType.forValue(unknownTypeString)).isEqualTo(RoninUserType.Unknown(unknownTypeString))
        }
    }
}
