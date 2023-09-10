package com.projectronin.kafka.clients

import com.projectronin.common.ResourceId
import com.projectronin.kafka.clients.MeteredProducer.Metrics.FLUSH_TIMER
import com.projectronin.kafka.clients.MeteredProducer.Metrics.SEND_TIMER
import com.projectronin.kafka.data.RoninEvent
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

class MeteredProducerTest {
    private data class Foo(val bar: String)

    private val meterRegistry = mockk<MeterRegistry>(relaxed = true)
    private val event = RoninEvent(
        source = "producer_unit_test",
        type = "foo",
        resourceId = ResourceId("foo", "17"),
        dataSchema = "url-to-foo",
        data = Foo("17")
    )
    private val record = ProducerRecord("test.topic", null, "foo/17", event)

    @Test
    fun `flush is metered and calls producer flush`() {
        val mockProducer = mockk<Producer<String, RoninEvent<Foo>>>(relaxed = true)
        val producer = MeteredProducer(mockProducer, meterRegistry)
        producer.flush()
        verify(exactly = 1) { mockProducer.flush() }
        verify(exactly = 1) { meterRegistry.timer(FLUSH_TIMER) }
    }

    @Test
    fun `flush calls producer flush with no meter registry`() {
        val mockProducer = mockk<Producer<String, RoninEvent<Foo>>>(relaxed = true)
        val producer: MeteredProducer<String, RoninEvent<Foo>> = MeteredProducer(mockProducer)
        producer.flush()
        verify(exactly = 1) { mockProducer.flush() }
    }

    @Test
    fun `send success with metrics no callback`() {
        val mockProducer = MockProducer(
            true,
            StringSerializer(),
            Serializer<RoninEvent<Foo>> { _, _ -> "_".toByteArray() }
        )
        val producer = MeteredProducer(mockProducer, meterRegistry)

        producer.send(record)

        assertThat(mockProducer.history().size == 1)
        assertThat(mockProducer.history()[0]).isEqualTo(record)

        verify(exactly = 1) {
            meterRegistry.timer(
                SEND_TIMER,
                "success",
                "true",
                "topic",
                "test.topic"
            )
        }
    }

    @Test
    fun `send failure with metrics no callback`() {
        val mockProducer = mockk<Producer<String, RoninEvent<Foo>>>(relaxed = true)
        val producer = MeteredProducer(mockProducer, meterRegistry)

        val metadata = mockk<RecordMetadata>()
        every { mockProducer.send(any(), any()) } answers {
            val block = secondArg<Callback>()
            block.onCompletion(null, RuntimeException("boom"))
            CompletableFuture.completedFuture(metadata)
        }

        producer.send(record)

        verify(exactly = 1) {
            meterRegistry.timer(
                SEND_TIMER,
                "success",
                "false",
                "topic",
                "test.topic"
            )
        }
    }

    @Test
    fun `send success with metrics with callback`() {
        val mockProducer = MockProducer(
            true,
            StringSerializer(),
            Serializer<RoninEvent<Foo>> { _, _ -> "_".toByteArray() }
        )
        val producer = MeteredProducer(mockProducer, meterRegistry)

        var metadata: RecordMetadata? = null
        producer.send(record) { m, _ -> metadata = m }

        assertThat(mockProducer.history().size == 1)
        assertThat(mockProducer.history()[0]).isEqualTo(record)
        assertThat(metadata).isNotNull()

        verify(exactly = 1) {
            meterRegistry.timer(
                SEND_TIMER,
                "success",
                "true",
                "topic",
                "test.topic"
            )
        }
    }

    @Test
    fun `send failure with metrics with callback`() {
        val mockProducer = mockk<Producer<String, RoninEvent<Foo>>>(relaxed = true)
        val producer = MeteredProducer(mockProducer, meterRegistry)

        val metadata = mockk<RecordMetadata>()
        every { mockProducer.send(any(), any()) } answers {
            val block = secondArg<Callback>()
            block.onCompletion(null, RuntimeException("Boom"))
            CompletableFuture.completedFuture(metadata)
        }

        var exception: Exception? = null
        producer.send(record) { _, e -> exception = e }

        assertThat(exception).isNotNull()
        assertThat(exception?.message).isEqualTo("Boom")
        verify(exactly = 1) {
            meterRegistry.timer(
                SEND_TIMER,
                "success",
                "false",
                "topic",
                "test.topic"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `async send success with metrics`() = runTest {
        val mockProducer = MockProducer(
            true,
            StringSerializer(),
            Serializer<RoninEvent<Foo>> { _, _ -> "_".toByteArray() }
        )
        val producer = MeteredProducer(mockProducer, meterRegistry)

        producer.asyncSend(record)

        assertThat(mockProducer.history().size == 1)
        assertThat(mockProducer.history()[0]).isEqualTo(record)

        verify(exactly = 1) {
            meterRegistry.timer(
                SEND_TIMER,
                "success",
                "true",
                "topic",
                "test.topic"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `async send failure with metrics`() {
        val runtimeException = RuntimeException("Boom")
        runTest {
            val mockProducer = mockk<Producer<String, RoninEvent<Foo>>>(relaxed = true)
            val producer = MeteredProducer(mockProducer, meterRegistry)

            val metadata = mockk<RecordMetadata>()
            every { mockProducer.send(any(), any()) } answers {
                val block = secondArg<Callback>()
                block.onCompletion(null, runtimeException)
                CompletableFuture.completedFuture(metadata)
            }

            assertThrows<RuntimeException> {
                producer.asyncSend(record)
            }

            verify(exactly = 1) {
                meterRegistry.timer(
                    SEND_TIMER,
                    "success",
                    "false",
                    "topic",
                    "test.topic"
                )
            }
        }
    }
}
