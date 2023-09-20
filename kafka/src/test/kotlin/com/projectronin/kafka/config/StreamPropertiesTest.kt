package com.projectronin.kafka.config

import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventDeserializer.Companion.RONIN_DESERIALIZATION_TYPES_CONFIG
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.streams.StreamsConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StreamPropertiesTest {

    @Test
    fun `defaults test`() {
        val clusterProps = ClusterProperties(bootstrapServers = "kafka:9092", saslUsername = "user", saslPassword = "pass")
        val props = StreamProperties(clusterProperties = clusterProps, "appId")

        assertThat(props[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo("kafka:9092")
        assertThat(props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo("SASL_SSL")
        assertThat(props[SaslConfigs.SASL_MECHANISM]).isEqualTo("SCRAM-SHA-512")
        assertThat(props[SaslConfigs.SASL_JAAS_CONFIG]).isNotNull

        assertThat(props[StreamsConfig.APPLICATION_ID_CONFIG]).isEqualTo("appId")
        assertThat(props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG])
            .isEqualTo("org.apache.kafka.common.serialization.Serdes\$StringSerde")
        assertThat(props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG])
            .isEqualTo("com.projectronin.kafka.serialization.RoninEventSerde")
        assertThat(props[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG])
            .isEqualTo("org.apache.kafka.streams.errors.LogAndFailExceptionHandler")
    }

    @Test
    fun `Test adding deserialization types`() {
        val clusterProps = ClusterProperties(bootstrapServers = "kafka:9092", SecurityProtocol.PLAINTEXT)
        val props = StreamProperties(clusterProperties = clusterProps, "appId")

        props.addDeserializationType("javaClass", StringSerde::class.java)
        props.addDeserializationType("kotlinClass", RoninEvent::class)
        assertThat(props[RONIN_DESERIALIZATION_TYPES_CONFIG])
            .isEqualTo(
                "javaClass:org.apache.kafka.common.serialization.Serdes\$StringSerde," +
                    "kotlinClass:com.projectronin.kafka.data.RoninEvent"
            )
    }
}
