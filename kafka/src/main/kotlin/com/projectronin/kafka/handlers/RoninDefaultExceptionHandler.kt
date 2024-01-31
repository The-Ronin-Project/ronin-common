package com.projectronin.kafka.handlers

import com.projectronin.common.telemetry.addToDDTraceSpan
import mu.KLogger
import mu.KotlinLogging
import org.apache.kafka.streams.errors.MissingSourceTopicException
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler

class RoninDefaultExceptionHandler : StreamsUncaughtExceptionHandler {
    private val logger: KLogger = KotlinLogging.logger { }

    override fun handle(exception: Throwable?): StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse {
        if (exception is MissingSourceTopicException) {
            logger.error("A Kafka Topic did not exist. $exception")
            exception.addToDDTraceSpan()
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT
        }
        return LogAndRestartUncaughtExceptionHandler().handle(exception)
    }
}
