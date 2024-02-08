package com.projectronin.common.metrics

import io.micrometer.core.instrument.MeterRegistry

object RoninMetrics {
    private var meterRegistry: MeterRegistry? = null
    fun setRegistry(registry: MeterRegistry?) = run { meterRegistry = registry }

    val registry: MeterRegistry
        get() = requireNotNull(meterRegistry) { "Registry is null" }

    fun registryOrNull(): MeterRegistry? = meterRegistry
}
