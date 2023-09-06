package com.projectronin.kafka.handlers

import org.apache.kafka.streams.errors.ProductionExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogAndContinueProductionExceptionHandlerTest {
    private val handler = LogAndContinueProductionExceptionHandler()

    @Test
    fun `configure doesn't do diddly`() {
        handler.configure(HashMap<String, Any?>())
        // No exceptions were thrown!
    }

    @Test
    fun `handle logs the error and continues`() {
        val response = handler.handle(null, Exception("some exception"))
        assertThat(response).isEqualTo(ProductionExceptionHandler.ProductionExceptionHandlerResponse.CONTINUE)
    }
}
