package com.projectronin.kafka.listeners

import org.apache.kafka.streams.KafkaStreams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogStateListenerTest {
    @Test
    fun `listener invokes param`() {
        var ran = false
        val listener = LogStateListener("appId") { n, o ->
            assertThat(n).isEqualTo(KafkaStreams.State.CREATED)
            assertThat(o).isEqualTo(KafkaStreams.State.RUNNING)
            ran = true
        }

        assertThat(ran).isEqualTo(false)
        listener.onChange(KafkaStreams.State.CREATED, KafkaStreams.State.RUNNING)
        assertThat(ran).isEqualTo(true)
    }
}
