package com.projectronin.auth.m2m

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class TokenResponse(
    @field:JsonProperty("access_token") val accessToken: String,
    val scope: String?,
    @field:JsonProperty("expires_in") val expiresIn: Long,
    @field:JsonProperty("token_type") val tokenType: String?
) {
    @field:JsonIgnore
    val expiresAt: Instant = Instant.now().plusSeconds((expiresIn * 0.9).toLong())
}
