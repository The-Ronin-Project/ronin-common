package com.projectronin.kafka.streams

import com.projectronin.common.telemetry.Tags
import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.StreamProperties
import com.projectronin.kafka.handlers.DeadLetterDeserializationExceptionHandler
import io.mockk.mockkStatic
import io.mockk.verify
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class StreamsExtKtTest {

    @Test
    fun `eventStream adds transformer`() {
        val builder = StreamsBuilder()
        builder.eventStream<String, String>("topic")
        val testDriver = TopologyTestDriver(/* topology = */
            builder.build(), /* config = */
            StreamProperties(
                ClusterProperties(
                    bootstrapServers = "localhost:9092",
                    securityProtocol = SecurityProtocol.PLAINTEXT
                ),
                applicationId = "test-app-id"
            ).apply {
                put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
                put(DeadLetterDeserializationExceptionHandler.DEAD_LETTER_TOPIC_CONFIG, "dlq")
            }
        )
        val createInputTopic =
            testDriver.createInputTopic("topic", Serdes.String().serializer(), Serdes.String().serializer())
        mockkStatic(MDC::class)

        createInputTopic.pipeInput("Hi Kent")

        verify {
            MDC.setContextMap(
                withArg { tags ->
                    Assertions.assertThat(tags[Tags.KAFKA_TOPIC_TAG]).isEqualTo("topic")
                    Assertions.assertThat(tags[Tags.KAFKA_PARTITION_TAG]).isEqualTo("0")
                    Assertions.assertThat(tags[Tags.KAFKA_OFFSET_TAG]).isEqualTo("0")
                }
            )
        }
    }
}
