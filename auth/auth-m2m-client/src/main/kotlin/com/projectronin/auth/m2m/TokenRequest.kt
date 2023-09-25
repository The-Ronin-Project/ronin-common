package com.projectronin.auth.m2m

import com.fasterxml.jackson.annotation.JsonProperty
import com.projectronin.auth.token.RoninLoginProfile

class TokenRequest(
    @field:JsonProperty("client_id") val clientId: String,
    @field:JsonProperty("client_secret") val clientSecret: String,
    val audience: String,
    scopes: List<String>?,
    @field:JsonProperty("requested_profile") val requestedProfile: RoninLoginProfile?
) {
    @field:JsonProperty("grant_type")
    val grantType: String = "client_credentials"

    val scope: String? = scopes?.joinToString(" ")
}
