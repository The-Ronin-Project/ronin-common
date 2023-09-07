package com.projectronin.kafka.config

import com.projectronin.kafka.data.RoninEvent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class KafkaConfiguration {
    @Bean
    open fun clusterProperties(
        @Value("ronin.kafka.bootstrap-servers")
        bootstrapServers: String,
        @Value("ronin.kafka.security-protocol")
        securityProtocol: String?,
        @Value("ronin.kafka.sasl.mechanism")
        saslMechanism: String?,
        @Value("ronin.kafka.sasl.username")
        saslUsername: String?,
        @Value("ronin.kafka.sasl.password")
        saslPassword: String?
    ): ClusterProperties {
        return ClusterProperties(
            bootstrapServers = bootstrapServers,
            securityProtocol = securityProtocol,
            saslMechanism = saslMechanism,
            saslUsername = saslUsername,
            saslPassword = saslPassword
        )
    }
}

@Configuration
open class ProducerConfiguration {
    @Bean(name = ["defaultProducerProperties"])
    open fun defaultProducerProperties(clusterProperties: ClusterProperties): ProducerProperties {
        return ProducerProperties(clusterProperties)
    }

    @Bean(name = ["defaultProducerProperties"], destroyMethod = "flush")
    open fun <T> kafkaProducer(producerProperties: ProducerProperties): Producer<String, RoninEvent<T>> {
        return KafkaProducer(producerProperties)
    }
}
