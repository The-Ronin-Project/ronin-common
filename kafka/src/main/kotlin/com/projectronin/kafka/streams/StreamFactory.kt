package com.projectronin.kafka.streams

import com.projectronin.kafka.handlers.LogAndRestartUncaughtExceptionHandler
import com.projectronin.kafka.listeners.LogStateListener
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties

interface StreamsFactory {
    companion object StreamsFactoryImpl : StreamsFactory {
        fun createStream(topology: Topology, configs: Properties): KafkaStreams {
            val applicationId: String = configs[StreamsConfig.APPLICATION_ID_CONFIG].toString()
            return KafkaStreams(topology, StreamsConfig(configs)).apply {
                Runtime.getRuntime().addShutdownHook(Thread(::close, "$applicationId-ShutdownThread"))
                setUncaughtExceptionHandler(LogAndRestartUncaughtExceptionHandler())
                setStateListener(LogStateListener(applicationId))
            }
        }
    }
}
