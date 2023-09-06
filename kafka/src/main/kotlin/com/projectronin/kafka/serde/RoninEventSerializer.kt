package com.projectronin.kafka.serde

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

    override fun serialize(topic: String, message: RoninEvent<T>?): ByteArray {
        throw SerializationException(
            "Serialize method without headers is not a " +
                "valid means to deserialize a RoninWrapper"
        )
    }

    override fun serialize(topic: String, headers: Headers, message: RoninEvent<T>?): ByteArray? {
        return message?.let {
            writeRoninWrapperHeaders(headers, message)
            writeHeaders(headers, message)
            mapper.writeValueAsBytes(message.data)
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
            headers.add(StringHeader(Header.TENANT_ID, message.tenantId))
        }
        message.patientId?.let {
            headers.add(StringHeader(Header.PATIENT_ID, message.patientId))
        }
        message.resource?.let {
            headers.add(StringHeader(Header.SUBJECT, message.resource.toString()))
        }
    }

    // Support Old consumers of RoninWrapper v1.0
    private fun writeRoninWrapperHeaders(headers: Headers, message: RoninEvent<T>) {
        headers.apply {
            add(StringHeader("ronin_wrapper_version", message.version))
            add(StringHeader("ronin_source_service", message.source))
            add(StringHeader("ronin_tenant_id", message.tenantId ?: "unknown"))
            add(StringHeader("ronin_data_type", message.type))
        }
    }
}

fun Headers.get(key: String) = lastHeader(key).value().decodeToString()
