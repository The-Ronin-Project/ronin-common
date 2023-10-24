package com.projectronin.kafka.streams

import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Properties

class KafkaStreamsKtTest {

    @Test
    fun `missing app id test`() {
        assertThrows<IllegalArgumentException> {
            kafkaStreams(Topology(), Properties())
        }
    }

    @Test
    fun `factory method test test`() {
        val streams = kafkaStreams(
            Topology().apply {
                addSource("someTopic", "topic")
            },
            Properties().apply {
                put(StreamsConfig.APPLICATION_ID_CONFIG, "appId")
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
            }
        )

        assertThat(streams).isNotNull
    }
}
