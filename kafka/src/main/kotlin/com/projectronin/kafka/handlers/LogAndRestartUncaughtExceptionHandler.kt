package com.projectronin.kafka.handlers

import com.projectronin.common.telemetry.addToDDTraceSpan
import mu.KLogger
import mu.KotlinLogging
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler

class LogAndRestartUncaughtExceptionHandler : StreamsUncaughtExceptionHandler {
    private val logger: KLogger = KotlinLogging.logger { }

    override fun handle(exception: Throwable?): StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse {
        logger.error("Some Exception that was not properly handled in the stream. \n$exception")
        exception?.addToDDTraceSpan()
        return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD
    }
}
