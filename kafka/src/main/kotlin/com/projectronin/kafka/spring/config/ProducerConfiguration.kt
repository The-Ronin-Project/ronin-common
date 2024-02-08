package com.projectronin.kafka.spring.config

import com.projectronin.kafka.clients.MeteredProducer
import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.ProducerProperties
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.util.Optional

@Configuration
open class ProducerConfiguration {
    @Bean(name = ["defaultProducerProperties"])
    @Lazy
    open fun defaultProducerProperties(clusterProperties: ClusterProperties): ProducerProperties {
        return ProducerProperties(clusterProperties)
    }

    @Bean(name = ["defaultProducer"], destroyMethod = "flush")
    @Lazy
    open fun <T> kafkaProducer(
        producerProperties: ProducerProperties,
        meterRegistry: Optional<MeterRegistry>
    ): Producer<String, T> {
        return MeteredProducer(KafkaProducer(producerProperties))
    }
}
