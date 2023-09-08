package com.projectronin.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProducerPropertiesTest {

    @Test
    fun `test defaults`() {
        val props = ProducerProperties(
            clusterProperties = ClusterProperties(bootstrapServers = "kafka:9092")
        )

        assertThat(props[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo("kafka:9092")
        assertThat(props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo("SASL_SSL")
        assertThat(props[SaslConfigs.SASL_MECHANISM]).isEqualTo("SCRAM-SHA-512")
        assertThat(props[SaslConfigs.SASL_JAAS_CONFIG]).isNotNull

        assertThat(props[ProducerConfig.ACKS_CONFIG]).isEqualTo("all")
        assertThat(props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG]).isEqualTo(false)
        assertThat(props[ProducerConfig.RETRIES_CONFIG]).isEqualTo(3)
        assertThat(props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION]).isEqualTo(1)
        assertThat(props[ProducerConfig.MAX_BLOCK_MS_CONFIG]).isEqualTo(60000L)
        assertThat(props[ProducerConfig.LINGER_MS_CONFIG]).isEqualTo(5)
        assertThat(props[ProducerConfig.COMPRESSION_TYPE_CONFIG]).isEqualTo("snappy")
        assertThat(props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG])
            .isEqualTo("org.apache.kafka.common.serialization.StringSerializer")
        assertThat(props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG])
            .isEqualTo("com.projectronin.kafka.serde.RoninEventSerializer")
    }

    @Test
    fun `test from configs`() {
        val props = ProducerProperties(
            mutableMapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "kafka:0000",
                "foo" to "bar"
            )
        )
        assertThat(props[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo("kafka:0000")
        assertThat(props["foo"]).isEqualTo("bar")
    }
}
