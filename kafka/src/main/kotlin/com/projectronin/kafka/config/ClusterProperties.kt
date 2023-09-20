package com.projectronin.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import java.util.Properties

class ClusterProperties(
    bootstrapServers: String,
    securityProtocol: String? = SECURITY_PROTOCOL_DEFAULT,
    saslMechanism: String? = SASL_MECHANISM_DEFAULT,
    saslUsername: String? = null,
    saslPassword: String? = null
) : Properties() {
    init {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)

        put(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
            when {
                securityProtocol.isNullOrEmpty() -> securityProtocol
                else -> SECURITY_PROTOCOL_DEFAULT
            }
        )

        put(SaslConfigs.SASL_MECHANISM, saslMechanism)
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"${saslUsername}\" password=\"${saslPassword}\";"
        )
    }

    companion object {
        const val SECURITY_PROTOCOL_DEFAULT = "SASL_SSL"
        const val SASL_MECHANISM_DEFAULT = "SCRAM-SHA-512"
    }
}
