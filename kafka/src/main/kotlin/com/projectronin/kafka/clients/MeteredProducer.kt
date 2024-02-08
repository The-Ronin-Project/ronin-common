package com.projectronin.kafka.clients

import com.projectronin.common.metrics.RoninMetrics
import com.projectronin.common.metrics.record
import com.projectronin.common.telemetry.Tags.KAFKA_OFFSET_TAG
import com.projectronin.common.telemetry.Tags.KAFKA_PARTITION_TAG
import com.projectronin.common.telemetry.Tags.KAFKA_TOPIC_TAG
import com.projectronin.common.telemetry.addToDDTraceSpan
import mu.KLogger
import mu.KotlinLogging
import mu.withLoggingContext
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class MeteredProducer<K, V>(val producer: Producer<K, V>) :
    Producer<K, V> by producer {

    private val logger: KLogger = KotlinLogging.logger { }
    private val timeSource = TimeSource.Monotonic

    object Metrics {
        const val SEND_TIMER = "roninkafka.producer.send"
        const val FLUSH_TIMER = "roninkafka.producer.flush"
    }

    override fun send(record: ProducerRecord<K, V>): Future<RecordMetadata> {
        return send(record, callback = { _, _ -> })
    }

    override fun send(record: ProducerRecord<K, V>, callback: Callback): Future<RecordMetadata> {
        val topic = record.topic()

        val startMark = timeSource.markNow()
        return producer.send(record) { metadata, exception ->
            val success: String =
                when (exception) {
                    null -> {
                        withLoggingContext(metadata?.mdc ?: emptyMap()) {
                            logger.debug("successfully sent record {} metadata: `{}`", record.key(), metadata)
                        }
                        "true"
                    }

                    else -> {
                        exception.addToDDTraceSpan()
                        withLoggingContext(
                            mapOf(
                                KAFKA_TOPIC_TAG to record.topic()
                            )
                        ) {
                            logger.warn("Exception ${exception.message} sending record metadata: `$metadata`")
                        }
                        "false"
                    }
                }

            RoninMetrics.registryOrNull()?.timer(
                Metrics.SEND_TIMER,
                "success",
                success,
                "topic",
                topic
            )?.record(startMark.elapsedNow())

            callback.onCompletion(metadata, exception)
        }
    }

    override fun flush() {
        val timeTaken = timeSource.measureTime {
            producer.flush()
        }
        RoninMetrics.registryOrNull()?.timer(Metrics.FLUSH_TIMER)?.record(timeTaken)
    }
}

suspend fun <K, V> Producer<K, V>.asyncSend(record: ProducerRecord<K, V>): RecordMetadata {
    return suspendCoroutine { continuation ->
        send(record) { metadata, exception ->
            exception?.let(continuation::resumeWithException) ?: continuation.resume(metadata)
        }
    }
}

val RecordMetadata.mdc: Map<String, String?>
    get() = mapOf(
        KAFKA_TOPIC_TAG to topic().toString(),
        KAFKA_PARTITION_TAG to partition().toString(),
        KAFKA_OFFSET_TAG to offset().toString()
    )
