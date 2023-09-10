package com.projectronin.kafka.spring

import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.ProducerProperties
import com.projectronin.kafka.spring.config.ProducerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class ProducerConfigurationTest {
    private data class Foo(val bar: String)
    private data class Bar(val foo: String)

    @Test
    fun `test producer creation`() {
        val config = ProducerConfiguration()
        val producerProperties = ProducerProperties(ClusterProperties("localhost:9092"))
        val fooProducer = config.kafkaProducer<Foo>(producerProperties, Optional.empty())
        val barProducer = config.kafkaProducer<Bar>(producerProperties, Optional.empty())

        assertThat(fooProducer).isNotNull
        assertThat(barProducer).isNotNull
    }
}
