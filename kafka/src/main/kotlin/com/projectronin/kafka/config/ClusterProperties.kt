package com.projectronin.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import java.util.Properties

class ClusterProperties(
    bootstrapServers: String,
    securityProtocol: String? = "SASL_SSL",
    saslMechanism: String? = "SCRAM-SHA-512",
    saslUsername: String? = null,
    saslPassword: String? = null
) : Properties() {
    init {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol)
        put(SaslConfigs.SASL_MECHANISM, saslMechanism)
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"${saslUsername}\" password=\"${saslPassword}\";"
        )
    }
}
