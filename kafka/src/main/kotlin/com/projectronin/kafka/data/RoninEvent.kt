package com.projectronin.kafka.data

import com.projectronin.common.ResourceId
import java.time.Instant
import java.util.UUID

data class RoninEvent<T>(
    val id: UUID = UUID.randomUUID(),
    val time: Instant = Instant.now(),
    val version: String = DEFAULT_VERSION,
    val source: String,
    val tenantId: String? = null,
    val patientId: String? = null,
    val dataSchema: String,
    val dataContentType: String = DEFAULT_CONTENT_TYPE,
    val data: T? = null,
    val type: String,
    val resource: ResourceId? = null
) {
    companion object {
        internal const val DEFAULT_VERSION = "2"
        internal const val DEFAULT_CONTENT_TYPE = "application/json"
    }
}
