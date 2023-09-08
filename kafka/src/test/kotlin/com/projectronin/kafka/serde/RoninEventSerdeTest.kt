package com.projectronin.kafka.serde

import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoninEventSerdeTest {
    private data class Stuff(val id: String)

    @Test
    fun `test public constructor creates new`() {
        val serde = RoninEventSerde<Stuff>()

        assertThat(serde.serializer()).isInstanceOf(RoninEventSerializer::class.java)
        assertThat(serde.deserializer()).isInstanceOf(RoninEventDeserializer::class.java)
    }

    @Test
    fun `test configure and close pass through`() {
        val serializer = mockk<RoninEventSerializer<Stuff>>(relaxed = true)
        val deserializer = mockk<RoninEventDeserializer<Stuff>>(relaxed = true)
        val serde = RoninEventSerde(serializer, deserializer)

        val configs = mutableMapOf(
            "some" to "config"
        )
        serde.configure(configs, false)
        verify(exactly = 1) { serializer.configure(configs, false) }
        verify(exactly = 1) { deserializer.configure(configs, false) }

        serde.close()
        verify(exactly = 1) { serializer.close() }
        verify(exactly = 1) { deserializer.close() }
    }
}
