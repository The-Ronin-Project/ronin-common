package com.projectronin.kafka.streams

import com.projectronin.common.telemetry.Tags
import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.StreamProperties
import io.mockk.mockkStatic
import io.mockk.verify
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
            ) {
                put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
                deadLetterTopic("dlq")
            }
        )
        val createInputTopic =
            testDriver.createInputTopic("topic", Serdes.String().serializer(), Serdes.String().serializer())
        mockkStatic(MDC::class)

        createInputTopic.pipeInput("Hi Kent")

        verify {
            MDC.setContextMap(
                withArg { tags ->
                    assertThat(tags[Tags.KAFKA_TOPIC_TAG]).isEqualTo("topic")
                    assertThat(tags[Tags.KAFKA_PARTITION_TAG]).isEqualTo("0")
                    assertThat(tags[Tags.KAFKA_OFFSET_TAG]).isEqualTo("0")
                }
            )
        }
    }

    @Test
    fun `test stream factory method`() {
        var i: Int = 0
        val topology = stream<String, String>("topic") {
                kStream ->
            kStream.peek { _, _ -> i++ }
        }
        assertThat(topology).isNotNull
        val description = topology.describe()
        val nodes = description.subtopologies().first().nodes()
        assertThat(nodes.count()).isEqualTo(3)
        val transformer = nodes.find { n -> n.name().equals("MDC_TRANSFORMER") }
        assertThat(transformer).isNotNull
        assertThat(transformer?.predecessors()?.first()?.name()?.startsWith("KSTREAM-SOURCE"))
    }

    @Test
    fun `empty topic throws exception`() {
        assertThrows<IllegalArgumentException> {
            stream<String, String>("") { kStream ->
                kStream.groupByKey()
            }
        }
    }

    @Test
    fun `topic list is works`() {
        var i: Int = 0
        val topology = stream<String, String>(listOf("topic1", "topic2")) {
                kStream ->
            kStream.peek { _, _ -> i++ }
        }
        assertThat(topology).isNotNull
        val description = topology.describe()
        val nodes = description.subtopologies().first().nodes()
        assertThat(nodes.count()).isEqualTo(3)
        val transformer = nodes.find { n -> n.name().equals("MDC_TRANSFORMER") }
        assertThat(transformer).isNotNull
        assertThat(transformer?.predecessors()?.first()?.name()?.startsWith("KSTREAM-SOURCE"))
        val topicSet =
            (topology.describe().subtopologies().first().nodes().first() as InternalTopologyBuilder.Source).topicSet()
        assertThat(topicSet.size).isEqualTo(2)
        assertThat(topicSet.contains("topic1"))
        assertThat(topicSet.contains("topic2"))
    }

    @Test
    fun `empty topic in list is invalid`() {
        val message = assertThrows<IllegalArgumentException> {
            stream<String, String>(emptyList()) { kStream ->
                kStream.groupByKey()
            }
        }.message
        assertThat(message).isEqualTo("topics cannot be empty list")
    }

    @Test
    fun `empty topic only in throws exception`() {
        assertThrows<IllegalArgumentException> {
            stream<String, String>(listOf("", " ")) { kStream ->
                kStream.groupByKey()
            }
        }
    }
}
