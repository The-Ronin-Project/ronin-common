package com.projectronin.common.telemetry

import io.opentracing.log.Fields
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

fun Throwable.addToDDTraceSpan(exceptionUniqueId: String? = null) {
    GlobalTracer.get().activeSpan()?.apply {
        setTag(Tags.ERROR, true)
        log(
            mutableMapOf(
                Fields.MESSAGE to message,
                Fields.ERROR_OBJECT to this@addToDDTraceSpan
            ).also {
                if (exceptionUniqueId != null) {
                    it[RoninFields.ERROR_ID] = exceptionUniqueId
                }
            }
        )
    }
}
