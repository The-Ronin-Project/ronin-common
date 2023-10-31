package com.projectronin.test.kafka

import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class TestConsumer<T>(
    bootstrapServers: String,
    topic: Topic,
    consumerGroup: String = UUID.randomUUID().toString()
) : AutoCloseable {
    private val consumer = KafkaConsumer<String, RoninEvent<T>>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroup,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to RoninEventDeserializer::class.qualifiedName,
            RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG to
                RoninEventDeserializer.createDeserializationTypeString(topic.deserializationTypes)
        )
    )

    init {
        consumer.subscribe(mutableListOf(topic.topic))
    }

    fun consume(
        expectedCount: Int = 1,
        timeoutSeconds: Long = 30L
    ): List<ConsumerRecord<String, RoninEvent<T>>> {
        val timeSource = TimeSource.Monotonic
        val endBy = timeSource.markNow().plus(timeoutSeconds.seconds)

        return consume { recordCount ->
            recordCount >= expectedCount || endBy.compareTo(timeSource.markNow()) < 0
        }
    }

    fun consume(
        until: (resultCount: Int) -> Boolean = { rc -> rc > 0 }
    ): List<ConsumerRecord<String, RoninEvent<T>>> {
        val results = mutableListOf<ConsumerRecord<String, RoninEvent<T>>>()

        while (!until.invoke(results.size)) {
            val records: ConsumerRecords<String, RoninEvent<T>> = consumer.poll(Duration.ofMillis(100))
            for (record in records) {
                results.add(record)
            }
        }
        return results
    }

    override fun close() {
        consumer.close()
    }
}
