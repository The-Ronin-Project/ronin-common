package com.projectronin.common.telemetry

import io.opentracing.Span

fun Span.addTags(tags: Map<String, String?>) {
    tags.entries.forEach { setTag(it.key, it.value) }
}
