package com.projectronin.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.projectronin.auth.token.RoninClaims
import com.projectronin.auth.token.RoninUserType

interface RoninClaimsAuthentication : RoninAuthentication {
    companion object {
        const val roninClaimsKey = "urn:projectronin:authorization:claims:version:1"
    }

    val roninClaimMap: Map<String, Any>?
    val objectMapper: ObjectMapper

    fun decodeRoninClaimsFromMap(): RoninClaims = when (val claim = roninClaimMap) {
        null -> RoninClaims(null)
        else -> objectMapper.convertValue(claim)
    }
    override val tenantId: String
        get() = roninClaims.user?.loginProfile?.accessingTenantId ?: ""

    override val userId: String
        get() = roninClaims.user?.id ?: ""

    override val udpId: String?
        get() = roninClaims.user?.loginProfile?.let { profile ->
            when (roninClaims.user?.userType) {
                RoninUserType.Provider -> profile.accessingProviderUdpId
                RoninUserType.Patient -> profile.accessingPatientUdpId
                else -> null
            }
        }

    override val providerRoninId: String?
        get() = roninClaims.user?.loginProfile?.accessingProviderUdpId

    override val patientRoninId: String?
        get() = roninClaims.user?.loginProfile?.accessingPatientUdpId

    override val userFirstName: String
        get() = roninClaims.user?.name?.givenName?.firstOrNull() ?: ""

    override val userLastName: String
        get() = roninClaims.user?.name?.familyName ?: ""

    override val userFullName: String
        get() = roninClaims.user?.name?.fullText ?: ""

    override val roninClaims: RoninClaims
        get() = decodeRoninClaimsFromMap()
}
