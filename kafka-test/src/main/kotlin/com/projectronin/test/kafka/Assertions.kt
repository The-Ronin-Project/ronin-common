package com.projectronin.test.kafka

import com.projectronin.kafka.data.RoninEvent
import org.apache.kafka.clients.consumer.ConsumerRecord

class Assertions {
    @Suppress("LongParameterList")
    fun <T> assertProduces(
        bootstrapServers: String,
        topic: Topic,
        expectedCount: Int = 1,
        timeoutSeconds: Long = 30L,
        consecutiveEmptyCount: Int = 4,
        block: () -> Unit
    ): List<ConsumerRecord<String, RoninEvent<T>>> {
        val consumer = TestConsumer<T>(bootstrapServers, topic)

        var i = consecutiveEmptyCount
        consumer.consume { recordCount ->
            when (recordCount) {
                0 -> {
                    i--
                    println("No records consumed. Waiting for $i more empty polls")
                }

                else -> {
                    i = consecutiveEmptyCount
                    println("$recordCount Records Found")
                }
            }
            i > 0
        }

        block.invoke()

        return consumer.consume(expectedCount, timeoutSeconds)
    }
}
