package com.projectronin.kafka.config

import com.projectronin.kafka.serialization.RoninEventSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.record.CompressionType
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import java.util.concurrent.TimeUnit

private const val DEFAULT_RETRIES = 3
private const val DEFAULT_LINGER_MS = 5
private const val DEFAULT_ACKS = "all"

class ProducerProperties(val clusterProperties: Properties) : Properties() {
    constructor(configs: MutableMap<String, *>) : this(Properties()) {
        putAll(configs)
    }

    init {
        putAll(clusterProperties)
        put(ProducerConfig.ACKS_CONFIG, com.projectronin.kafka.config.DEFAULT_ACKS)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false)
        put(ProducerConfig.RETRIES_CONFIG, com.projectronin.kafka.config.DEFAULT_RETRIES)
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1)
        put(ProducerConfig.MAX_BLOCK_MS_CONFIG, TimeUnit.MINUTES.toMillis(1))
        put(ProducerConfig.LINGER_MS_CONFIG, com.projectronin.kafka.config.DEFAULT_LINGER_MS)
        put(ProducerConfig.COMPRESSION_TYPE_CONFIG, CompressionType.SNAPPY.name)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, RoninEventSerializer::class.qualifiedName)
    }
}
