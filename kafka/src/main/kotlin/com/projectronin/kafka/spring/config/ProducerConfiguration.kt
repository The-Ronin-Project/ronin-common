package com.projectronin.kafka.spring.config

import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.config.ProducerProperties
import com.projectronin.kafka.data.RoninEvent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class ProducerConfiguration {
    @Bean(name = ["defaultProducerProperties"])
    open fun defaultProducerProperties(clusterProperties: ClusterProperties): ProducerProperties {
        return ProducerProperties(clusterProperties)
    }

    @Bean(name = ["defaultProducer"], destroyMethod = "flush")
    open fun <T> kafkaProducer(producerProperties: ProducerProperties): Producer<String, RoninEvent<T>> {
        return KafkaProducer(producerProperties)
    }
}
