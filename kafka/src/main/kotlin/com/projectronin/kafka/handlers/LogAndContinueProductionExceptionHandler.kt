package com.projectronin.kafka.handlers

import com.projectronin.common.telemetry.Tags.KAFKA_PARTITION_TAG
import com.projectronin.common.telemetry.Tags.KAFKA_TOPIC_TAG
import com.projectronin.common.telemetry.addToDDTraceSpan
import mu.KotlinLogging
import mu.withLoggingContext
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.errors.ProductionExceptionHandler
import java.lang.Exception

class LogAndContinueProductionExceptionHandler : ProductionExceptionHandler {
    private val logger = KotlinLogging.logger {}

    override fun configure(configs: MutableMap<String, *>?) {
        // Ignore
    }

    override fun handle(
        record: ProducerRecord<ByteArray, ByteArray>?,
        exception: Exception?
    ): ProductionExceptionHandler.ProductionExceptionHandlerResponse {
        exception?.addToDDTraceSpan()

        withLoggingContext(
            mapOf(
                KAFKA_TOPIC_TAG to record?.topic(),
                KAFKA_PARTITION_TAG to record?.partition().toString()
            )
        ) {
            logger.warn("There was an exception (${exception?.message}) writing the message to Kafka from the Stream.")
        }
        return ProductionExceptionHandler.ProductionExceptionHandlerResponse.CONTINUE
    }
}
