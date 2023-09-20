package com.projectronin.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.auth.token.RoninAuthenticationScheme
import com.projectronin.auth.token.RoninAuthenticationSchemeType
import com.projectronin.auth.token.RoninClaims
import com.projectronin.auth.token.RoninLoginProfile
import com.projectronin.auth.token.RoninName
import com.projectronin.auth.token.RoninUser
import com.projectronin.auth.token.RoninUserIdentity
import com.projectronin.auth.token.RoninUserIdentityType
import com.projectronin.auth.token.RoninUserType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.GrantedAuthority

class RoninClaimsAuthenticationTest {
    @Test
    fun roninClaimsKey() {
        assertThat(RoninClaimsAuthentication.roninClaimsKey).isEqualTo(
            "urn:projectronin:authorization:claims:version:1"
        )
    }

    @Test
    fun `it works`() {
        val claims = RoninClaims(
            user = RoninUser(
                id = "userId",
                userType = RoninUserType.RoninEmployee,
                authenticationSchemes = listOf(
                    RoninAuthenticationScheme(
                        type = RoninAuthenticationSchemeType.Auth0M2M,
                        tenantId = "tenantId",
                        id = "schemeId"
                    )
                ),
                name = RoninName("Joe Blow", "Blow", listOf("Joe"), prefix = emptyList(), suffix = emptyList()),
                identities = listOf(
                    RoninUserIdentity(
                        type = RoninUserIdentityType.M2MClientId,
                        tenantId = "tenantId",
                        id = "userIdentityId"
                    )
                ),
                preferredTimeZone = null,
                loginProfile = RoninLoginProfile(
                    accessingTenantId = "tenantId",
                    accessingExternalPatientId = null,
                    accessingPatientUdpId = null,
                    accessingProviderUdpId = null
                )
            )
        )

        val authentication = TestRoninClaimsAuthentication(
            tokenValue = "tokenValue",
            roninClaimMap = objectMapper.readValue(
                objectMapper.writeValueAsString(claims)
            ),
            objectMapper
        )

        with(authentication) {
            assertThat(roninClaims).usingRecursiveComparison().isEqualTo(claims)
            assertThat(tenantId).isEqualTo("tenantId")
            assertThat(userId).isEqualTo(claims.user!!.id)
            assertThat(udpId).isNull()
            assertThat(userFullName).isEqualTo("Joe Blow")
            assertThat(userFirstName).isEqualTo("Joe")
            assertThat(userLastName).isEqualTo("Blow")
            assertThat(patientRoninId).isNull()
            assertThat(providerRoninId).isNull()
        }
    }
}

private class TestRoninClaimsAuthentication(
    override val tokenValue: String,
    override val roninClaimMap: Map<String, Any>?,
    override val objectMapper: ObjectMapper
) : RoninClaimsAuthentication {
    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        TODO("Not yet implemented")
    }

    override fun getCredentials(): Any {
        TODO("Not yet implemented")
    }

    override fun getDetails(): Any {
        TODO("Not yet implemented")
    }

    override fun getPrincipal(): Any {
        TODO("Not yet implemented")
    }

    override fun isAuthenticated(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setAuthenticated(isAuthenticated: Boolean) {
        TODO("Not yet implemented")
    }
}

private val objectMapper = jsonMapper {
    addModule(kotlinModule())
}
