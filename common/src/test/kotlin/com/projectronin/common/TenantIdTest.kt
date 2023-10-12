package com.projectronin.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TenantIdTest {

    @ParameterizedTest
    @ValueSource(strings = [ "apposnd", "mdaoc", "ronin"])
    fun `non-standard ids work`(value: String) {
        assertDoesNotThrow { TenantId(value) }
    }

    fun `apposnd works`() {
        assertThat(TenantId.apposnd.value).isEqualTo("apposnd")
    }

    fun `mdaoc works`() {
        assertThat(TenantId.mdaoc.value).isEqualTo("mdaoc")
    }

    fun `ronin works`() {
        assertThat(TenantId.ronin.value).isEqualTo("ronin")
    }

    @Test
    fun `random generates valid ids`() {
        assertDoesNotThrow {
            repeat(100) {
                TenantId.random()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [ "abcdefgh", "aaaaaaaa" ])
    fun `all letters ok`(value: String) {
        assertDoesNotThrow { TenantId(value) }
    }

    @ParameterizedTest
    @ValueSource(strings = [ "01234567", "00000000" ])
    fun `all numbers ok`(value: String) {
        assertDoesNotThrow { TenantId(value) }
    }

    @Test
    fun `no uppercase letters`() {
        val id = TenantId.random().value.uppercase()

        val message = assertThrows<IllegalArgumentException> {
            TenantId(id)
        }.message

        assertThat(message).isEqualTo("invalid tenant id: $id")
    }

    @ParameterizedTest
    @ValueSource(strings = [ "1", "12", "123", "1234", "12345", "123456", "1234567", "123456789"])
    fun `must be 8 characters`(badValue: String) {
        assertThrows<IllegalArgumentException> { TenantId(badValue) }
    }

    @Test
    fun `no letter i`() {
        assertThrows<IllegalArgumentException> { TenantId("1234567i") }
    }

    @Test
    fun `no letter l`() {
        assertThrows<IllegalArgumentException> { TenantId("1234567l") }
    }

    @Test
    fun `no letter o`() {
        assertThrows<IllegalArgumentException> { TenantId("1234567o") }
    }

    @Test
    fun `no letter u`() {
        assertThrows<IllegalArgumentException> { TenantId("1234567u") }
    }
}
