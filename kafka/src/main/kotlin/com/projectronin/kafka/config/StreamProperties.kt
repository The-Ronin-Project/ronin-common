package com.projectronin.kafka.config

import com.projectronin.kafka.handlers.DeadLetterDeserializationExceptionHandler
import com.projectronin.kafka.handlers.LogAndContinueProductionExceptionHandler
import com.projectronin.kafka.serialization.RoninEventDeserializer
import com.projectronin.kafka.serialization.RoninEventSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties
import kotlin.reflect.KClass

class StreamProperties(clusterProperties: Properties, applicationId: String, block: StreamPropertyBuilder.() -> Unit = {}) : Properties() {
    init {
        apply {
            putAll(clusterProperties)
            put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde::class.java.name)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, RoninEventSerde::class.qualifiedName)
            put(
                StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                DeadLetterDeserializationExceptionHandler::class.java.name
            )
            put(
                StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueProductionExceptionHandler::class.java.name
            )
        }
        block.invoke(StreamPropertyBuilder(this))
    }

    @Deprecated("Migrated to a DSL")
    fun addDeserializationType(type: String, typeClass: KClass<*>) {
        addTypeClass(type, typeClass.qualifiedName!!)
    }

    @Deprecated("Migrated to a DSL", ReplaceWith("{addDeserializationType<T>(type)}"))
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

    class StreamPropertyBuilder(private val properties: Properties) {
        inline fun <reified T : Any> addDeserializationType(type: String) {
            addDeserializationTypeClass(type, T::class.java.name)
        }

        fun put(key: String, value: Any) {
            properties[key] = value
        }

        fun addDeserializationTypeClass(type: String, typeClass: String) {
            var typeConfig = properties.getProperty(RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG, "")
            if (typeConfig.isNotEmpty()) {
                typeConfig += ","
            }
            typeConfig += "$type:$typeClass"
            properties[RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG] = typeConfig
        }
    }
}
