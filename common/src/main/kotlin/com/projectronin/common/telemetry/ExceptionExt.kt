package com.projectronin.common.telemetry

import io.opentracing.log.Fields
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

fun Exception.addToDDTraceSpan() {
    GlobalTracer.get().activeSpan()?.apply {
        setTag(Tags.ERROR, true)
        log(
            mutableMapOf(
                Fields.ERROR_OBJECT to this@addToDDTraceSpan
            )
        )
    }
}

fun Throwable.addToDDTraceSpan() {
    GlobalTracer.get().activeSpan()?.apply {
        setTag(Tags.ERROR, true)
        log(
            mutableMapOf(
                Fields.MESSAGE to message
            )
        )
    }
}
