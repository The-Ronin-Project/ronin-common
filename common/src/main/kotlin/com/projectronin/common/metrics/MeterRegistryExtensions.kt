package com.projectronin.common.metrics

import io.micrometer.core.instrument.Timer
import kotlin.time.Duration

fun Timer.record(duration: Duration) {
    this.record(duration.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
}
