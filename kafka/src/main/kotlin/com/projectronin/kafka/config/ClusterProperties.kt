package com.projectronin.kafka.config

import com.projectronin.kafka.exceptions.ConfigurationException
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.Properties

class ClusterProperties(
    bootstrapServers: String,
    securityProtocol: SecurityProtocol? = null,
    saslMechanism: String? = null,
    saslUsername: String? = null,
    saslPassword: String? = null
) : Properties() {
    init {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)

        val protocol = securityProtocol ?: SECURITY_PROTOCOL_DEFAULT
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol.name)

        if (protocol == SecurityProtocol.SASL_SSL || protocol == SecurityProtocol.SASL_PLAINTEXT) {
            if (saslUsername == null) {
                throw ConfigurationException("saslUsername is required when security protocol is ${protocol.name}")
            }
            if (saslPassword == null) {
                throw ConfigurationException("saslPassword is required when security protocol is ${protocol.name}")
            }

            put(SaslConfigs.SASL_MECHANISM, saslMechanism ?: SASL_MECHANISM_DEFAULT)
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                    "username=\"${saslUsername}\" password=\"${saslPassword}\";"
            )
        }
    }

    companion object {
        val SECURITY_PROTOCOL_DEFAULT = SecurityProtocol.SASL_SSL
        const val SASL_MECHANISM_DEFAULT = "SCRAM-SHA-512"
    }
}
