package com.projectronin.common.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoninMetricsTest {

    @Test
    fun `test null registry`() {
        assertThat(RoninMetrics.registryOrNull()).isNull()
        assertThrows<IllegalArgumentException> { RoninMetrics.registry }
    }

    @Test
    fun `test real registry`() {
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)
        RoninMetrics.setRegistry(meterRegistry)
        assertThat(RoninMetrics.registryOrNull()).isEqualTo(meterRegistry)
        assertThat(RoninMetrics.registry).isEqualTo(meterRegistry)
        RoninMetrics.setRegistry(null)
    }
}
