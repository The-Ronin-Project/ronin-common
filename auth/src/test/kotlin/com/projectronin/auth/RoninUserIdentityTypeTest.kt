package com.projectronin.auth

import com.projectronin.auth.token.RoninUserIdentityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoninUserIdentityTypeTest {
    @Test
    fun `forValue works with known type strings`() {
        mapOf(
            "PROVIDER_UDP_ID" to RoninUserIdentityType.ProviderUdpId,
            "PROVIDER_FHIR_ID" to RoninUserIdentityType.ProviderFhirId,
            "PATIENT_UDP_ID" to RoninUserIdentityType.PatientUdpId,
            "PATIENT_SMS_ID" to RoninUserIdentityType.PatientSmsId,
            "GOOGLE_ACCOUNT_ID" to RoninUserIdentityType.GoogleAccountId,
            "AUTH0_ACCOUNT_ID" to RoninUserIdentityType.Auth0AccountId,
            "M2M_CLIENT_ID" to RoninUserIdentityType.M2MClientId,
            "MDA_EPIC_USER_ID" to RoninUserIdentityType.MDAEpicUserID,
            "MDA_USER_PROV_NPI" to RoninUserIdentityType.MDAUserProvNPI
        ).forEach { (knownTypeString, userType) ->
            assertThat(RoninUserIdentityType.forValue(knownTypeString)).isEqualTo(userType)
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
            assertThat(RoninUserIdentityType.forValue(unknownTypeString))
                .isEqualTo(RoninUserIdentityType.Unknown(unknownTypeString))
        }
    }
}
