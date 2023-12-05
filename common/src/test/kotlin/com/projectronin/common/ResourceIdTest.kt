package com.projectronin.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ResourceIdTest {
    private val simpleResource = ResourceId("sometype", "someid")
    private val withAuthorityResource = ResourceId("ronin.authority.sometype", "someid")

    @Test
    fun `simple resource returns correct values`() {
        with(simpleResource) {
            assertThat(type).isEqualTo("sometype")
            assertThat(id).isEqualTo("someid")
            assertThat(toString()).isEqualTo("sometype/someid")
        }
    }

    @Test
    fun `with authority resource returns correct values`() {
        with(withAuthorityResource) {
            assertThat(type).isEqualTo("ronin.authority.sometype")
            assertThat(id).isEqualTo("someid")
            assertThat(toString()).isEqualTo("ronin.authority.sometype/someid")
        }
    }

    @Test
    fun `parse from simple string`() {
        assertThat(ResourceId.parseOrNull("sometype/someid")).isEqualTo(simpleResource)
    }

    @Test
    fun `parse returns null when null passed in`() {
        assertThat(ResourceId.parseOrNull(null)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = [ "invalid", "in/val/id", "ronin.source" ])
    fun `invalid format throws exception`(value: String) {
        assertThrows<IllegalArgumentException> {
            ResourceId.parseOrNull(value)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [ "type/id", "ronin.source.type/id" ])
    fun `valid format does not throw exception`(value: String) {
        assertDoesNotThrow {
            ResourceId.parseOrNull(value)
        }
    }
}
