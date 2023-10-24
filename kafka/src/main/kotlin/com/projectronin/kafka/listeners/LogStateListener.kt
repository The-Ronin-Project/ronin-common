package com.projectronin.kafka.listeners

import mu.KLogger
import mu.KotlinLogging
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KafkaStreams.StateListener

class LogStateListener(
    private val applicationId: String,
    private val listener: (newState: KafkaStreams.State, oldState: KafkaStreams.State) -> (Unit) = { _, _ -> }
) : StateListener {
    private val logger: KLogger = KotlinLogging.logger { }

    override fun onChange(newState: KafkaStreams.State, oldState: KafkaStreams.State) {
        logger.info("Stream $applicationId is changing state from $oldState to $newState")
        listener.invoke(newState, oldState)
    }
}
