package com.projectronin.kafka.clients

import io.micrometer.core.instrument.MeterRegistry
import mu.KLogger
import mu.KotlinLogging
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MeteredProducer<K, V>(val producer: Producer<K, V>, private val meterRegistry: MeterRegistry? = null) :
    Producer<K, V> by producer {

    private val logger: KLogger = KotlinLogging.logger { }

    object Metrics {
        const val SEND_TIMER = "roninkafka.producer.send"
        const val FLUSH_TIMER = "roninkafka.producer.flush"
    }

    override fun send(record: ProducerRecord<K, V>): Future<RecordMetadata> {
        return send(record, callback = null)
    }

    override fun send(record: ProducerRecord<K, V>, callback: Callback?): Future<RecordMetadata> {
        val topic = record.topic()

        val start = System.currentTimeMillis()
        return producer.send(record) { metadata, exception ->
            val success: String =
                when (exception) {
                    null -> {
                        logger.debug("successfully sent record {} metadata: `{}`", record.key(), metadata)
                        "true"
                    }

                    else -> {
                        logger.warn("Exception ${exception.message} sending record metadata: `$metadata`")
                        "false"
                    }
                }

            meterRegistry?.timer(
                Metrics.SEND_TIMER,
                "success", success,
                "topic", topic
            )?.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
            callback?.onCompletion(metadata, exception)
        }
    }

    override fun flush() {
        val start = System.currentTimeMillis()
        producer.flush()
        meterRegistry?.timer(Metrics.FLUSH_TIMER)?.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
    }

    suspend fun asyncSend(record: ProducerRecord<K, V>) = suspendCoroutine<RecordMetadata> { continuation ->
        send(record) { metadata, exception ->
            exception?.let(continuation::resumeWithException) ?: continuation.resume(metadata)
        }
    }
}