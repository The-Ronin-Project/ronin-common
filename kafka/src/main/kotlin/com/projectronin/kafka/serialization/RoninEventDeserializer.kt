package com.projectronin.kafka.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_CONTENT_TYPE
import com.projectronin.kafka.data.RoninEventHeaders
import com.projectronin.kafka.exceptions.ConfigurationException
import com.projectronin.kafka.exceptions.EventHeaderMissing
import com.projectronin.kafka.exceptions.UnknownEventType
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

class RoninEventDeserializer<T> : Deserializer<RoninEvent<T>> {
    private lateinit var typeMap: Map<String, KClass<*>>
    private val mapper: ObjectMapper = com.projectronin.kafka.config.MapperFactory.mapper

    companion object {
        const val RONIN_DESERIALIZATION_TYPES_CONFIG = "ronin.json.deserializer.types"

        fun createDeserializationTypeString(typeMap: Map<String, String>): String {
            return typeMap.map { entry -> "${entry.key}:${entry.value}" }
                .reduceRight { f, s -> "$f,$s" }
        }
    }

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        super.configure(configs, isKey)

        val types = configs[RONIN_DESERIALIZATION_TYPES_CONFIG] as String?
        typeMap = makeTypeMap(types) ?: throw ConfigurationException(
            "Config $RONIN_DESERIALIZATION_TYPES_CONFIG was not configured for the deserialization types."
        )
    }

    override fun deserialize(topic: String, bytes: ByteArray?): RoninEvent<T> {
        throw UnsupportedOperationException("Deserialize method without headers is not supported by this deserializer")
    }

    override fun deserialize(topic: String, headers: Headers, bytes: ByteArray?): RoninEvent<T> {
        val roninHeaders = headers
            .filter { it.value() != null && it.value().isNotEmpty() }
            .associate { it.key() to it.value().decodeToString() }

        val isWrapper = roninHeaders["ronin_data_type"]?.run { typeMap.containsKey(this) } ?: false

        return when (isWrapper) {
            true -> {
                fromRoninWrapper(topic, roninHeaders, bytes)
            }

            else -> {
                when (roninHeaders[RoninEventHeaders.VERSION]) {
                    "1.0", "2" -> {
                        // RoninEvent version found 2, which supports v1.0 as well
                        fromV2(topic, roninHeaders, bytes)
                    }

                    null -> {
                        // No version headers found of any kind
                        throw EventHeaderMissing(listOf(RoninEventHeaders.VERSION))
                    }

                    else -> {
                        // Attempt to support newer versions hoping they were compatible
                        fromLatest(topic, roninHeaders, bytes)
                    }
                }
            }
        }
    }

    private fun fromLatest(topic: String, roninHeaders: Map<String, String>, bytes: ByteArray?): RoninEvent<T> {
        return fromV2(topic, roninHeaders, bytes)
    }

    private fun fromV2(topic: String, roninHeaders: Map<String, String>, bytes: ByteArray?): RoninEvent<T> {
        roninHeaders.validate(topic, RoninEventHeaders.required)

        val id = roninHeaders.getValue(RoninEventHeaders.ID)
        val type = roninHeaders.getValue(RoninEventHeaders.TYPE)

        val data: T? = deserializeData(topic, type, bytes)

        return RoninEvent(
            id = UUID.fromString(id),
            time = Instant.parse(roninHeaders[RoninEventHeaders.TIME]),
            version = roninHeaders.getValue(RoninEventHeaders.VERSION),
            dataSchema = roninHeaders.getValue(RoninEventHeaders.DATA_SCHEMA),
            type = type,
            source = roninHeaders.getValue(RoninEventHeaders.SOURCE),
            dataContentType = roninHeaders.getValue(RoninEventHeaders.CONTENT_TYPE),
            resourceId = ResourceId.parseOrNull(roninHeaders[RoninEventHeaders.SUBJECT]),
            tenantId = roninHeaders[RoninEventHeaders.TENANT_ID]?.let { TenantId(it) },
            patientId = roninHeaders[RoninEventHeaders.PATIENT_ID]?.let { PatientId(it) },
            data = data
        )
    }

    // Checks to see if the message was written using the deprecated RoninWrapper and
// constructs the RoninEvent appropriately
    private fun fromRoninWrapper(topic: String, roninHeaders: Map<String, String>, bytes: ByteArray?): RoninEvent<T> {
        val wrapperVersion = "ronin_wrapper_version"
        val sourceService = "ronin_source_service"
        val tenantId = "ronin_tenant_id"
        val dataType = "ronin_data_type"

        roninHeaders.validate(topic, listOf(wrapperVersion, sourceService, tenantId, dataType))

        val type = roninHeaders.getValue(dataType)
        val data: T? = deserializeData(topic, type, bytes)

        return RoninEvent(
            id = UUID(0, 0),
            source = roninHeaders.getValue(sourceService),
            dataContentType = DEFAULT_CONTENT_TYPE,
            dataSchema = "unknown",
            type = roninHeaders.getValue(dataType),
            version = roninHeaders.getValue(wrapperVersion),
            tenantId = TenantId(roninHeaders.getValue(tenantId)),
            data = data
        )
    }

    private fun deserializeData(topic: String, type: String, bytes: ByteArray?): T? {
        val filtered = typeMap.filter { entry -> type.startsWith(entry.key) }
        val valueClass: KClass<*> = when {
            filtered.containsKey(type) -> filtered.getValue(type)
            filtered.isNotEmpty() -> filtered.entries.first().value
            else -> throw UnknownEventType(null, type, topic)
        }

        @Suppress("UNCHECKED_CAST")
        val data: T? = bytes?.let { mapper.readValue(bytes, valueClass.java) as T }
        return data
    }

    private fun makeTypeMap(config: String?): Map<String, KClass<out Any>>? =
        try {
            config?.split(",")?.associate {
                val (left, right) = it.split(":", ignoreCase = true, limit = 2)
                left.trim() to Class.forName(right.trim()).kotlin
            }
        } catch (_: IndexOutOfBoundsException) {
            throw ConfigurationException(
                "$RONIN_DESERIALIZATION_TYPES_CONFIG was improperly formatted and could not be parsed."
            )
        }
}

private fun Map<String, String>.validate(topic: String, required: List<String>): Map<String, String> {
    this.keys
        .let {
            val missing = required - it
            if (missing.isNotEmpty()) {
                throw EventHeaderMissing(missing, topic)
            }
        }
    return this
}
