package com.projectronin.kafka.serde

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoninEventSerdeTest {
    private data class Stuff(val id: String)

    @Test
    fun testSerde() {
        val serde = RoninEventSerde<Stuff>()

        assertThat(serde.serializer()).isInstanceOf(RoninEventSerializer::class.java)
        assertThat(serde.deserializer()).isInstanceOf(RoninEventDeserializer::class.java)
    }
}
