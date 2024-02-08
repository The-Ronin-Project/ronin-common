package com.projectronin.kafka.spring.config

import com.projectronin.common.metrics.RoninMetrics
import com.projectronin.kafka.config.ClusterProperties
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
open class KafkaConfiguration : ApplicationContextAware {

    @Bean
    @Lazy
    open fun clusterProperties(
        @Value("\${ronin.kafka.bootstrap-servers}")
        bootstrapServers: String,
        @Value("\${ronin.kafka.security-protocol:#{null}}")
        securityProtocol: String? = null,
        @Value("\${ronin.kafka.sasl.mechanism:#{null}}")
        saslMechanism: String? = null,
        @Value("\${ronin.kafka.sasl.username:#{null}}")
        saslUsername: String? = null,
        @Value("\${ronin.kafka.sasl.password:#{null}}")
        saslPassword: String? = null
    ): ClusterProperties {
        return ClusterProperties(
            bootstrapServers = bootstrapServers,
            securityProtocol = securityProtocol?.let { SecurityProtocol.forName(it) },
            saslMechanism = saslMechanism,
            saslUsername = saslUsername,
            saslPassword = saslPassword
        )
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        RoninMetrics.setRegistry(applicationContext.getBean(MeterRegistry::class.java))
    }
}
