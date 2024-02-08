package com.projectronin.kafka.handlers

import com.projectronin.common.metrics.RoninMetrics
import com.projectronin.common.telemetry.addToDDTraceSpan
import com.projectronin.kafka.exceptions.ConfigurationException
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.streams.errors.DeserializationExceptionHandler
import org.apache.kafka.streams.processor.ProcessorContext

class DeadLetterDeserializationExceptionHandler : DeserializationExceptionHandler {
    private var configs: MutableMap<String, *> = mutableMapOf<String, Any>()
    private val logger = KotlinLogging.logger {}
    private var dlq: String? = null
    private val producer: Producer<ByteArray, ByteArray> by lazy {
        DeadLetterProducer.producer(configs)
    }

    object Metrics {
        const val DESERIALIZATION_EXCEPTION_METER = "roninkafka.deserialization.exception"
    }

    companion object {
        const val DEAD_LETTER_TOPIC_CONFIG = "ronin.dead.letter.topic"
    }

    override fun configure(configs: MutableMap<String, *>?) {
        configs?.let {
            this.configs = configs
        }

        dlq = this.configs[DEAD_LETTER_TOPIC_CONFIG] as String?
            ?: throw ConfigurationException("Missing required configuration. $DEAD_LETTER_TOPIC_CONFIG")
    }

    override fun handle(
        context: ProcessorContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: Exception?
    ): DeserializationExceptionHandler.DeserializationHandlerResponse {
        exception?.let {
            it.addToDDTraceSpan()
            logger.warn(
                "Exception Deserializing Message from" +
                    " ${record.topic()}-${record.partition()}@${record.offset()}. " +
                    "Failed with exception ${exception.message}. Sending to DLQ"
            )
            RoninMetrics.registryOrNull()?.counter(
                Metrics.DESERIALIZATION_EXCEPTION_METER,
                "topic", context.topic(),
                "partition", context.partition().toString(),
                "offset", context.offset().toString(),
                "message", it.message
            )
        }
        producer.send(
            ProducerRecord(
                /* topic = */ dlq,
                /* partition = */ null,
                /* key = */ record.key(),
                /* value = */ record.value(),
                /* headers = */ record.headers()
            )
        ) { recordMetadata: RecordMetadata?, ex: java.lang.Exception? ->
            recordMetadata?.let {
                logger.warn("Message forwarded to DLQ $dlq at $recordMetadata")
            }
            ex?.let {
                logger.warn("Attempts to write message to DLQ $dlq failed with exception ${ex.message}")
            }
        } ?: logger.warn("Cannot write to DLQ as the DLQ Producer was not created.")

        exception?.addToDDTraceSpan()
        return DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
    }
}
