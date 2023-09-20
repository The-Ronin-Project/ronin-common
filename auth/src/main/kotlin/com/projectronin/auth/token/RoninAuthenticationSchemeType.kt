package com.projectronin.auth.token

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

sealed class RoninAuthenticationSchemeType(@field:JsonValue val value: String) {

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun forValue(value: String): RoninAuthenticationSchemeType = when (value) {
            SmartOnFhir.value -> SmartOnFhir
            MDAToken.value -> MDAToken
            Auth0M2M.value -> Auth0M2M
            Auth0GoogleOauth.value -> Auth0GoogleOauth
            Auth0OTP.value -> Auth0OTP
            else -> Unknown(value)
        }
    }

    object SmartOnFhir : RoninAuthenticationSchemeType("SMART_ON_FHIR")
    object MDAToken : RoninAuthenticationSchemeType("MDA_TOKEN")
    object Auth0M2M : RoninAuthenticationSchemeType("AUTH0_M2M")
    object Auth0GoogleOauth : RoninAuthenticationSchemeType("AUTH0_GOOGLE_OAUTH")
    object Auth0UsernamePassword : RoninAuthenticationSchemeType("AUTH0_USERNAME_PASSWORD")
    object Auth0OTP : RoninAuthenticationSchemeType("AUTH0_OTP")
    internal class Unknown(value: String) : RoninAuthenticationSchemeType(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoninAuthenticationSchemeType) return false

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return value
    }
}
