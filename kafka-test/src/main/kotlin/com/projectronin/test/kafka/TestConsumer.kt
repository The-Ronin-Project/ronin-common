package com.projectronin.test.kafka

import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class TestConsumer<T>(
    bootstrapServers: String,
    consumerGroup: String,
    deserializationTypes: Map<String, String>
) {
    private val consumer = KafkaConsumer<String, RoninEvent<T>>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroup,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to RoninEventDeserializer::class.qualifiedName,
            RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG to
                RoninEventDeserializer.createDeserializationTypeString(deserializationTypes)
        )
    )

    fun consume(
        topic: String,
        expectedCount: Int = 1,
        timeoutSeconds: Long = 30L
    ): List<ConsumerRecord<String, RoninEvent<T>>> {
        if (!consumer.listTopics().containsKey(topic)) {
            consumer.subscribe(mutableListOf(topic))
        }

        val results = mutableListOf<ConsumerRecord<String, RoninEvent<T>>>()
        val timeSource = TimeSource.Monotonic
        val endBy = timeSource.markNow().plus(timeoutSeconds.seconds)

        while (results.size < expectedCount && endBy.compareTo(timeSource.markNow()) > 0) {
            val records: ConsumerRecords<String, RoninEvent<T>> = consumer.poll(Duration.ofMillis(100))
            for (record in records) {
                results.add(record)
            }
        }
        return results
    }

    fun close() {
        consumer.close()
    }
}
