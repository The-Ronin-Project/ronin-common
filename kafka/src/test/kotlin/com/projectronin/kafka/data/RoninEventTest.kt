package com.projectronin.kafka.data

import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_CONTENT_TYPE
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_VERSION
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "something.create"
        )

        assertThat(event.id).isEqualTo(testId)
        assertThat(Instant.now().isAfter(event.time)).isTrue()
        assertThat(event.version).isEqualTo(DEFAULT_VERSION)
        assertThat(event.dataContentType).isEqualTo(DEFAULT_CONTENT_TYPE)
    }

    @AfterEach
    fun `Remove UUID mockks`() {
        clearAllMocks()
        unmockkStatic(UUID::class)
    }
}