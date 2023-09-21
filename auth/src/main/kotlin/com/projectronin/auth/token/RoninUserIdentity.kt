package com.projectronin.auth.token

//     - type: PROVIDER_FHIR_ID
//       tenant: v7r1eczk
//       id: eSC7e62xM4tbHbRbARdo0kw3
class RoninUserIdentity(
    val type: RoninUserIdentityType,
    val tenantId: String?,
    val id: String?
)
