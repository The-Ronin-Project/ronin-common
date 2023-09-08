package com.projectronin.kafka.spring.config

import com.projectronin.kafka.config.ClusterProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class KafkaConfiguration {
    @Bean
    open fun clusterProperties(
        @Value("\${ronin.kafka.bootstrap-servers}")
        bootstrapServers: String,
        @Value("\${ronin.kafka.security-protocol:}")
        securityProtocol: String?,
        @Value("\${ronin.kafka.sasl.mechanism:}")
        saslMechanism: String?,
        @Value("\${ronin.kafka.sasl.username:}")
        saslUsername: String?,
        @Value("\${ronin.kafka.sasl.password:}")
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
