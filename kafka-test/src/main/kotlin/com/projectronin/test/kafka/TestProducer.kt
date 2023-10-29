package com.projectronin.test.kafka

import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.TimeUnit

class TestProducer<T>(bootstrapServers: String) {
    private val producer = KafkaProducer(
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        ),
        StringSerializer(),
        RoninEventSerializer<T>()
    )

    fun send(topic: String, key: String, event: RoninEvent<T>): RecordMetadata {
        return producer.send(
            ProducerRecord(topic, key, event)
        ).get(30L, TimeUnit.SECONDS)
    }

    fun close() {
        producer.close()
    }
}
