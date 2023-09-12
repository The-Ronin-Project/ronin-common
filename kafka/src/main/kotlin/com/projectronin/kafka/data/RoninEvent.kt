package com.projectronin.kafka.data

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
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
