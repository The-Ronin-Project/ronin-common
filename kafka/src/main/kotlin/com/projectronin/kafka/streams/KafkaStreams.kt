package com.projectronin.kafka.streams

import com.projectronin.kafka.handlers.LogAndRestartUncaughtExceptionHandler
import com.projectronin.kafka.listeners.LogStateListener
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties

fun kafkaStreams(topology: Topology, configs: Properties): KafkaStreams {
    val applicationId = requireNotNull(configs[StreamsConfig.APPLICATION_ID_CONFIG]?.toString()) {
        "${StreamsConfig.APPLICATION_ID_CONFIG} is a required configuration and was not supplied"
    }
    val streams = KafkaStreams(topology, StreamsConfig(configs)).apply {
        Runtime.getRuntime().addShutdownHook(Thread(::close, "$applicationId-ShutdownThread"))
        setUncaughtExceptionHandler(LogAndRestartUncaughtExceptionHandler())
        setStateListener(LogStateListener(applicationId))
    }
    return streams
}
