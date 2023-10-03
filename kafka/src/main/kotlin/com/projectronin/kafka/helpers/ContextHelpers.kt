package com.projectronin.kafka.helpers

import com.projectronin.kafka.data.RoninEvent
import mu.withLoggingContext

fun <V, R> withContext(topic: String, key: String, event: RoninEvent<V>, fn: () -> R): R =
    withContext(
        mapOf(
            RoninEventContext.TOPIC.contextName to topic,
            RoninEventContext.KEY.contextName to key
        ) + event.toContext(),
        fn
    )

private fun <R> withContext(context: Map<String, String?>, fn: () -> R): R =
    withLoggingContext(context) {
        withTracingContext(context) {
            fn()
        }
    }

private fun <R> withTracingContext(context: Map<String, String?>, fn: () -> R): R {
    // TODO: Datadog or OpenTracing?
//    val span = GlobalTracer.get().activeSpan()
//
//    // don't bother setting context or trying to mark the span as having error'd if there isn't a span
//    if (span == null) {
//        return fn()
//    }
//
//    context.forEach { (k, v) -> span.setTag(k, v) }
//
//    try {
//        return fn()
//    } catch (ex: Exception) {
//        span.setTag(Tags.ERROR, true)
//        span.log(mapOf(Fields.ERROR_OBJECT to ex))
//        throw ex
//    }
    return fn()
}

private enum class RoninEventContext(val contextName: String) {
    TOPIC("event.topic"),
    KEY("event.key"),
    ID("event.id"),
    SCHEMA("event.schema"),
    TYPE("event.type"),
    CLASS("event.class"),
    TENANT_ID("event.tenant_id"),
    PATIENT_ID("event.patient_id"),
    RESOURCE_ID("event.resource.id"),
    RESOURCE_TYPE("event.resource.type"),
    RESOURCE_PROFILES("event.resource.profiles")
}

private fun RoninEvent<*>.toContext(): Map<String, String?> =
    mapOf(
        RoninEventContext.ID to id.toString(),
        RoninEventContext.SCHEMA to dataSchema,
        RoninEventContext.TYPE to type,
        RoninEventContext.CLASS to data?.javaClass?.name,
        RoninEventContext.TENANT_ID to tenantId?.toString(),
        RoninEventContext.PATIENT_ID to patientId?.toString(),
        RoninEventContext.RESOURCE_TYPE to resourceId?.type,
        RoninEventContext.RESOURCE_ID to resourceId?.id
        // TODO: is it worth either depending on the fhir models jar or reflection grossness to include this?
        // RoninEventContext.RESOURCE_PROFILES to (data as? Resource)?.meta?.profile.orEmpty().joinToString(", ")
    ).mapKeys { it.key.contextName }
