package com.projectronin.kafka.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.data.StringHeader
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer
import java.time.format.DateTimeFormatter
import com.projectronin.kafka.data.RoninEventHeaders as Header

class RoninEventSerializer<T> : Serializer<RoninEvent<T>> {
    private val mapper: ObjectMapper = com.projectronin.kafka.config.MapperFactory.mapper
    private val instantFormatter = DateTimeFormatter.ISO_INSTANT
    private val legacyOptions: MutableSet<String> = mutableSetOf()

    companion object {
        const val RONIN_SERIALIZE_LEGACY_CONFIG = "ronin.serializer.legacy"
        const val LEGACY_WRAPPER_OPTION = "WRAPPER"
    }

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        super.configure(configs, isKey)
        if (configs.containsKey(RONIN_SERIALIZE_LEGACY_CONFIG)) {
            legacyOptions.addAll(configs[RONIN_SERIALIZE_LEGACY_CONFIG].toString().split(","))
        }
    }

    override fun serialize(topic: String, message: RoninEvent<T>?): ByteArray {
        throw SerializationException(
            "Serialize method without headers is not a " +
                "valid means to deserialize a RoninWrapper"
        )
    }

    override fun serialize(topic: String, headers: Headers, message: RoninEvent<T>?): ByteArray? {
        return message?.let {
            headers.clearPrevious()

            if (legacyOptions.contains(LEGACY_WRAPPER_OPTION)) {
                writeRoninWrapperHeaders(headers, it)
            }

            writeHeaders(headers, it)
            it
                .dataOrNull()
                ?.let { data -> mapper.writeValueAsBytes(data) }
        }
    }

    private fun writeHeaders(headers: Headers, message: RoninEvent<T>) {
        headers.apply {
            add(StringHeader(Header.ID, message.id.toString()))
            add(StringHeader(Header.SOURCE, message.source))
            add(StringHeader(Header.VERSION, message.version))
            add(StringHeader(Header.TYPE, message.type))
            add(StringHeader(Header.CONTENT_TYPE, message.dataContentType))
            add(StringHeader(Header.DATA_SCHEMA, message.dataSchema))
            add(StringHeader(Header.TIME, instantFormatter.format(message.time)))
        }

        message.tenantId?.let {
            headers.add(StringHeader(Header.TENANT_ID, message.tenantId.value))
        }
        message.patientId?.let {
            headers.add(StringHeader(Header.PATIENT_ID, message.patientId.value))
        }
        message.resourceId?.let {
            headers.add(StringHeader(Header.SUBJECT, message.resourceId.toString()))
        }
    }

    // Support Old consumers of RoninWrapper v1.0
    private fun writeRoninWrapperHeaders(headers: Headers, message: RoninEvent<T>) {
        headers.apply {
            add(StringHeader("ronin_wrapper_version", message.version))
            add(StringHeader("ronin_source_service", message.source))
            add(StringHeader(Header.TENANT_ID, message.tenantId?.value ?: "unknown"))
            add(StringHeader("ronin_data_type", message.type))
        }
    }
}

private fun Headers.clearPrevious() {
    this.removeAll { h ->
        listOf(
            "ronin_wrapper_version",
            "ronin_source_service",
            "ronin_data_type",
            Header.TENANT_ID,
            Header.PATIENT_ID,
            Header.SUBJECT
        ).contains(h.key())
    }
}

fun Headers.get(key: String) = lastHeader(key)?.value()?.decodeToString()
