package com.projectronin.auth.token

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

sealed class RoninUserType(@field:JsonValue val value: String) {

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun forValue(value: String): RoninUserType = when (value) {
            Provider.value -> Provider
            Patient.value -> Patient
            Service.value -> Service
            IntegrationTest.value -> IntegrationTest
            RoninEmployee.value -> RoninEmployee
            else -> Unknown(value)
        }
    }

    object Provider : RoninUserType("PROVIDER")
    object Patient : RoninUserType("PATIENT")
    object Service : RoninUserType("SERVICE")
    object IntegrationTest : RoninUserType("INTEGRATION_TEST")
    object RoninEmployee : RoninUserType("RONIN_EMPLOYEE")
    internal class Unknown(value: String) : RoninUserType(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoninUserType) return false

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return value
    }
}
