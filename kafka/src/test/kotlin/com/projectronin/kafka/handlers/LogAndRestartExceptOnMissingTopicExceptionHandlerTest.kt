package com.projectronin.kafka.handlers

import com.projectronin.common.telemetry.addToDDTraceSpan
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.streams.errors.MissingSourceTopicException
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class LogAndRestartExceptOnMissingTopicExceptionHandlerTest {

    @Test
    fun handleTest() {
        val handler = LogAndRestartExceptOnMissingTopicExceptionHandler()
        val missingTopicException = mockk<MissingSourceTopicException>(relaxed = true)
        val exception = mockk<Exception>(relaxed = true)
        val responseShutdown = handler.handle(missingTopicException)
        verify(exactly = 1) { missingTopicException.addToDDTraceSpan() }
        Assertions.assertThat(responseShutdown).isEqualTo(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT)
        val responseRestart = handler.handle(exception)
        verify(exactly = 1) { exception.addToDDTraceSpan() }
        Assertions.assertThat(responseRestart).isEqualTo(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD)
    }
}
