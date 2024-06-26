package com.projectronin.kafka.handlers

import com.projectronin.common.metrics.RoninMetrics
import com.projectronin.kafka.exceptions.ConfigurationException
import com.projectronin.kafka.handlers.DeadLetterDeserializationExceptionHandler.Companion.DEAD_LETTER_TOPIC_CONFIG
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
import org.apache.kafka.streams.processor.ProcessorContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeadLetterDeserializationExceptionHandlerTest {
    private val handler = DeadLetterDeserializationExceptionHandler()
    private val mockProducer = mockk<KafkaProducer<ByteArray, ByteArray>>(relaxed = true)
    private val meterRegistry = mockk<MeterRegistry>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(DeadLetterProducer)
        every { DeadLetterProducer.producer(any()) } returns (mockProducer)

        mockkObject(RoninMetrics)
        every { RoninMetrics.registryOrNull() } returns (meterRegistry)
    }

    @Test
    fun `test deserialization exception`() {
        val configs = mutableMapOf(
            DEAD_LETTER_TOPIC_CONFIG to "dlq-topic"
        )
        handler.configure(configs)
        val record = ConsumerRecord("original-topic", 0, 13L, "key".toByteArray(), "value".toByteArray())
        val context = mockk<ProcessorContext>(relaxed = true)
        every { context.toString() } returns ("Context Information")
        every { context.topic() } returns (record.topic())
        every { context.partition() } returns (record.partition())
        every { context.offset() } returns (record.offset())

        val dlqRecord = slot<ProducerRecord<ByteArray, ByteArray>>()
        val callback = slot<Callback>()
        val metadata = mockk<RecordMetadata>(relaxed = true)

        val future = null
        every { mockProducer.send(capture(dlqRecord), capture(callback)) } returns (future)

        val handlerResponse = handler.handle(context, record, Exception("Blew Up"))

        assertThat(handlerResponse).isEqualTo(CONTINUE)
        assertThat(String(dlqRecord.captured.key())).isEqualTo("key")
        assertThat(String(dlqRecord.captured.value())).isEqualTo("value")

        // Callbacks can't throw exceptions. In this case just telemetry the outcome
        callback.captured.onCompletion(metadata, null)
        callback.captured.onCompletion(null, Exception("Not Thrown"))

        verify(exactly = 1) {
            meterRegistry.counter(
                DeadLetterDeserializationExceptionHandler.Metrics.DESERIALIZATION_EXCEPTION_METER,
                "topic", "original-topic",
                "partition", "0",
                "offset", "13",
                "message", "Blew Up"
            )
        }
    }

    @Test
    fun `test deserialization exception with no meter registry`() {
        mockkObject(RoninMetrics)
        every { RoninMetrics.registryOrNull() } returns (null)
        val configs = mutableMapOf(
            DEAD_LETTER_TOPIC_CONFIG to "dlq-topic"
        )
        handler.configure(configs)
        val record = ConsumerRecord("original-topic", 0, 13L, "key".toByteArray(), "value".toByteArray())
        val context = mockk<ProcessorContext>(relaxed = true)
        every { context.toString() } returns ("Context Information")

        val dlqRecord = slot<ProducerRecord<ByteArray, ByteArray>>()
        val callback = slot<Callback>()
        val metadata = mockk<RecordMetadata>(relaxed = true)

        val future = null
        every { mockProducer.send(capture(dlqRecord), capture(callback)) } returns (future)

        val handlerResponse = handler.handle(context, record, Exception("Blew Up"))

        assertThat(handlerResponse).isEqualTo(CONTINUE)
        assertThat(String(dlqRecord.captured.key())).isEqualTo("key")
        assertThat(String(dlqRecord.captured.value())).isEqualTo("value")

        // Callbacks can't throw exceptions. In this case just telemetry the outcome
        callback.captured.onCompletion(metadata, null)
        callback.captured.onCompletion(null, Exception("Not Thrown"))

        verify(exactly = 0) {
            meterRegistry.counter(
                DeadLetterDeserializationExceptionHandler.Metrics.DESERIALIZATION_EXCEPTION_METER,
                "topic", "original-topic",
                "partition", "0",
                "offset", "13",
                "message", "Blew Up"
            )
        }
    }

    @Test
    fun `test valid configuration`() {
        val configs = mutableMapOf<String, String>()
        assertThatThrownBy {
            handler.configure(configs)
        }.isInstanceOf(ConfigurationException::class.java)
    }

    @Test
    fun `test null configuration`() {
        assertThatThrownBy {
            handler.configure(null)
        }.isInstanceOf(ConfigurationException::class.java)
    }
}
