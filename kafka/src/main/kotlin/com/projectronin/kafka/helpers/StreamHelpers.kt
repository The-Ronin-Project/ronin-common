package com.projectronin.kafka.helpers

import com.projectronin.common.ResourceId
import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.StreamProperties
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.handlers.DeadLetterDeserializationExceptionHandler
import com.projectronin.kafka.handlers.LogAndContinueProductionExceptionHandler
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.ForeachAction
import kotlin.reflect.KClass

interface SaveDeleteHandler<V> {
    fun save(event: V)

    fun delete(resourceId: ResourceId)
}

inline fun <reified V> saveDeleteStream(
    streamProperties: StreamProperties,
    topic: String,
    handler: SaveDeleteHandler<V>
): KafkaStreams {
    val topology = StreamsBuilder().apply {
        saveDeleteStream(topic, handler)
    }.build()

    return KafkaStreams(topology, StreamsConfig(streamProperties)).apply {
        // TODO: set whatever we decide the default StreamsUncaughtExceptionHandler handler should be
        // setUncaughtExceptionHandler { _ -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD }
    }
}

fun <V> StreamsBuilder.forEachStream(topic: String, handler: ForeachAction<String, RoninEvent<V>>) {
    stream<String, RoninEvent<V>>(topic).apply {
        foreach { key, value ->
            withContext(topic, key, value) {
                withRetry {
                    handler.apply(key, value)
                }
            }
        }
    }
}

fun <V> StreamsBuilder.saveDeleteStream(topic: String, handler: SaveDeleteHandler<V>) =
    forEachStream(topic, handler.toForEachAction())

fun <V> SaveDeleteHandler<V>.toForEachAction(): ForeachAction<String, RoninEvent<V>> =
    ForeachAction<String, RoninEvent<V>> { k, v ->
        when {
            v.type.endsWith(".create") -> save(v.data!!)
            v.type.endsWith(".update") -> save(v.data!!)
            v.type.endsWith(".delete") -> delete(v.resourceId!!)
            else -> throw IllegalArgumentException("Unknown event type provided to CreateUpdateDeleteRoninEventHandler: ${v.type}")
        }
    }

fun streamProperties(
    clusterConfig: ClusterProperties,
    applicationId: String,
    deserializationEventTypes: Map<String, KClass<*>>,
    dlqTopic: String
) = StreamProperties(clusterConfig, applicationId).apply {
    deserializationEventTypes.forEach { addDeserializationType(it.key, it.value) }

    put(
        StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
        DeadLetterDeserializationExceptionHandler::class.java
    )
    put(DeadLetterDeserializationExceptionHandler.DEAD_LETTER_TOPIC_CONFIG, dlqTopic)
    put(
        StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
        LogAndContinueProductionExceptionHandler::class.java
    )
}
