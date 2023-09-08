package com.projectronin.kafka.config

import com.projectronin.kafka.serde.RoninEventDeserializer
import com.projectronin.kafka.serde.RoninEventSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import java.util.*
import kotlin.reflect.KClass

class StreamProperties(clusterProperties: Properties, applicationId: String) : Properties() {
    init {
        putAll(clusterProperties)
        put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
        put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde::class.java.name)
        put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, RoninEventSerde::class.qualifiedName)
        put(
            StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
            LogAndFailExceptionHandler::class.java.name
        )
    }

    fun addDeserializationType(type: String, typeClass: KClass<*>) {
        addTypeClass(type, typeClass.qualifiedName!!)
    }

    fun addDeserializationType(type: String, typeClass: Class<*>) {
        addTypeClass(type, typeClass.name)
    }

    private fun addTypeClass(type: String, typeClass: String) {
        var typeConfig = getProperty(RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG, "")
        if (typeConfig.isNotEmpty()) {
            typeConfig += ","
        }
        typeConfig += "$type:$typeClass"
        put(RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG, typeConfig)
    }
}
