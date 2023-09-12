package com.projectronin.common.metrics

import io.micrometer.core.instrument.Timer

fun Timer.record(duration: kotlin.time.Duration) {
    this.record(duration.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
}
