package com.projectronin.kafka.handlers

import com.projectronin.common.telemetry.addToDDTraceSpan
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogAndRestartUncaughtExceptionHandlerTest {

    @Test
    fun handleTest() {
        val handler = LogAndRestartUncaughtExceptionHandler()
        val exception = mockk<Exception>(relaxed = true)
        val response = handler.handle(exception)
        verify(exactly = 1) { exception.addToDDTraceSpan() }
        assertThat(response).isEqualTo(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD)
    }
}
