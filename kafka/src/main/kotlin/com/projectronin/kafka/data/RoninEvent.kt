package com.projectronin.kafka.data

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.common.telemetry.Tags
import java.time.Instant
import java.util.UUID

data class RoninEvent<T>(
    val id: UUID = UUID.randomUUID(),
    val time: Instant = Instant.now(),
    val version: String = DEFAULT_VERSION,
    val source: String,
    val tenantId: TenantId? = null,
    val patientId: PatientId? = null,
    val dataSchema: String,
    val dataContentType: String = DEFAULT_CONTENT_TYPE,
    val data: T? = null,
    val type: String,
    val resourceId: ResourceId? = null
) {
    companion object {
        internal const val DEFAULT_VERSION = "2"
        internal const val DEFAULT_CONTENT_TYPE = "application/json"
    }
}

val RoninEvent<*>.mdc: Map<String, String?>
    get() = mapOf(
        "event.id" to id.toString(),
        "event.version" to version,
        Tags.TENANT_TAG to (tenantId?.toString()),
        Tags.PATIENT_TAG to (patientId?.toString()),
        "event.subject" to (resourceId?.toString()),
        "event.type" to type,
        "event.class" to (data?.javaClass?.name)
    )
