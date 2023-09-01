package com.projectronin.kafka.data

import com.projectronin.common.ResourceType
import com.projectronin.common.Services
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_CONTENT_TYPE
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_VERSION
import io.mockk.every
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class RoninEventTest {
    private data class Foo(val bar: String)

    @Test
    fun `Create RoninEvent validate default values`() {
        val testId = UUID.fromString("350e8400-e29b-41d4-a716-000000000000")
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.returns(testId)

        val event = RoninEvent<Foo>(
            source = Services.ASSETS,
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "${ResourceType.RONIN_DOCUMENT_REFERENCE}.create"
        )

        assertThat(event.id).isEqualTo(testId)
        assertThat(Instant.now().isAfter(event.time)).isTrue()
        assertThat(event.version).isEqualTo(DEFAULT_VERSION)
        assertThat(event.dataContentType).isEqualTo(DEFAULT_CONTENT_TYPE)
    }
}
