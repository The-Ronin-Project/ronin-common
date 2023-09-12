package com.projectronin.kafka.handlers

import com.projectronin.kafka.exceptions.ConfigurationException
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.streams.errors.DeserializationExceptionHandler
import org.apache.kafka.streams.processor.ProcessorContext

class DeadLetterDeserializationExceptionHandler : DeserializationExceptionHandler {
    private val logger = KotlinLogging.logger {}
    private var dlq: String? = null
    private var producer: Producer<ByteArray, ByteArray>? = null

    companion object {
        const val DEAD_LETTER_TOPIC_CONFIG = "ronin.dead.letter.topic"
    }

    override fun configure(configs: MutableMap<String, *>?) {
        configs?.let {
            dlq = configs[DEAD_LETTER_TOPIC_CONFIG] as String?
                ?: throw ConfigurationException("Missing required configuration. $DEAD_LETTER_TOPIC_CONFIG")
            producer = DeadLetterProducer.producer(configs)
        } ?: throw ConfigurationException("Missing required configuration. $DEAD_LETTER_TOPIC_CONFIG")
    }

    override fun handle(
        context: ProcessorContext?,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: Exception?
    ): DeserializationExceptionHandler.DeserializationHandlerResponse {
        producer?.send(
            ProducerRecord(
                dlq,
                null,
                record.key(),
                record.value(),
                record.headers()
            )
        ) { recordMetadata: RecordMetadata?, ex: java.lang.Exception? ->
            recordMetadata?.let {
                logger.warn(
                    "Exception Deserializing Message $context. " +
                        "Message forwarded to DLQ $dlq at $recordMetadata"
                )
            }
            ex?.let {
                logger.warn(
                    "Exception Deserializing Message $context. " +
                        "Attempts to write message to DLQ $dlq failed with exception ${ex.message}"
                )
            }
        } ?: logger.warn("Cannot write to DLQ as the DLQ Producer was not created.")
        return DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
    }
}
