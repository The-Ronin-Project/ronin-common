package com.projectronin.kafka.handlers

import com.projectronin.kafka.clients.MeteredProducer
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer

object DeadLetterProducer {
    private var dlqProducer: Producer<ByteArray, ByteArray>? = null
    var meterRegistry: MeterRegistry? = null

    fun producer(configs: MutableMap<String, *>): Producer<ByteArray, ByteArray> {
        return dlqProducer ?: createDLQProducer(configs)
    }

    private fun createDLQProducer(configs: MutableMap<String, *>): Producer<ByteArray, ByteArray> {
        val dlqConfigs = com.projectronin.kafka.config.ProducerProperties(configs)
        dlqConfigs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
        dlqConfigs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
        dlqProducer = MeteredProducer(KafkaProducer(dlqConfigs), meterRegistry)
        return dlqProducer!!
    }
}
