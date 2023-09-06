package com.projectronin.kafka.handlers

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.BytesSerializer

object DeadLetterProducer {
    private var dlqProducer: KafkaProducer<ByteArray, ByteArray>? = null

    fun producer(configs: MutableMap<String, *>): KafkaProducer<ByteArray, ByteArray> {
        return DeadLetterProducer.dlqProducer ?: createDLQProducer(configs)
    }

    private fun createDLQProducer(configs: MutableMap<String, *>): KafkaProducer<ByteArray, ByteArray> {
        val dlqConfigs = com.projectronin.kafka.config.ProducerProperties(configs)
        dlqConfigs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = BytesSerializer::class.java
        dlqConfigs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = BytesSerializer::class.java
        dlqProducer = KafkaProducer(dlqConfigs)
        return dlqProducer!!
    }
}
