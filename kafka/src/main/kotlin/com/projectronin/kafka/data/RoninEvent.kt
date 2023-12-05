package com.projectronin.kafka.data

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.common.telemetry.Tags
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
class RoninEvent<T>(
    val id: UUID = UUID.randomUUID(),
    val time: Instant = Instant.now(),
    val version: String = DEFAULT_VERSION,
    val source: String,
    val tenantId: TenantId? = null,
    val patientId: PatientId? = null,
    val dataSchema: String,
    val dataContentType: String = DEFAULT_CONTENT_TYPE,
    data: T? = null,
    val type: String,
    val resourceId: ResourceId? = null,
    val resourceVersion: Int? = null
) {
    companion object {
        internal const val DEFAULT_VERSION = "2"
        internal const val DEFAULT_CONTENT_TYPE = "application/json"
    }

    private val _data: T? = data
    val data: T
        get() = requireNotNull(_data) { "Data is null" }

    fun dataOrNull(): T? = _data

    val subject: String? = when {
        resourceId == null -> null
        resourceId.type.contains(".") -> "$resourceId"
        else -> "ronin.$source.$resourceId"
    }

    val mdc: Map<String, String?>
        get() = mapOf(
            Tags.RONIN_EVENT_ID_TAG to id.toString(),
            Tags.RONIN_EVENT_VERSION_TAG to version,
            Tags.RONIN_EVENT_TYPE_TAG to type,
            Tags.TENANT_TAG to (tenantId?.value)
        )
}

fun ResourceId.Companion.fromHeaderOrNull(header: String?): ResourceId? {
    return parseOrNull(header?.split(".")?.last())
}
