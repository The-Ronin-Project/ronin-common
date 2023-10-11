package com.projectronin.common.telemetry

import io.opentracing.Span

fun Span.addTags(tags: Map<String, String?>): Span {
    return tags.entries.fold(this) { acc, entry ->
        acc.setTag(entry.key, entry.value)
    }
}
