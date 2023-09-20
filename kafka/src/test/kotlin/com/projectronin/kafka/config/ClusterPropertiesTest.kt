package com.projectronin.kafka.config

import com.projectronin.kafka.exceptions.ConfigurationException
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClusterPropertiesTest {

    private val bootstrapServers = "bootstrap"
    private val username = "user"
    private val password = "password"

    @Test
    fun `PlainText Test`() {
        val properties = ClusterProperties(bootstrapServers, securityProtocol = SecurityProtocol.PLAINTEXT)

        assertThat(properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(bootstrapServers)
        assertThat(properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo(SecurityProtocol.PLAINTEXT.name)
        assertThat(properties.containsKey(SaslConfigs.SASL_MECHANISM)).isEqualTo(false)
        assertThat(properties.containsKey(SaslConfigs.SASL_JAAS_CONFIG)).isEqualTo(false)
    }

    @Test
    fun `No username throws exception`() {
        assertThrows<ConfigurationException> { ClusterProperties(bootstrapServers, saslPassword = password) }
    }

    @Test
    fun `No extra args throws exception`() {
        assertThrows<ConfigurationException> { ClusterProperties(bootstrapServers, saslUsername = username) }
    }

    @Test
    fun `No protocol passed in default`() {
        val properties = ClusterProperties(
            bootstrapServers,
            saslUsername = username,
            saslPassword = password
        )

        assertThat(properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(bootstrapServers)
        assertThat(properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo(SecurityProtocol.SASL_SSL.name)
        assertThat(properties[SaslConfigs.SASL_MECHANISM]).isEqualTo("SCRAM-SHA-512")
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(username)).isEqualTo(true)
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(password)).isEqualTo(true)
    }

    @Test
    fun `Default correct config`() {
        val properties = ClusterProperties(
            bootstrapServers,
            securityProtocol = SecurityProtocol.SASL_SSL,
            saslMechanism = null,
            saslUsername = username,
            saslPassword = password
        )

        assertThat(properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(bootstrapServers)
        assertThat(properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo(SecurityProtocol.SASL_SSL.name)
        assertThat(properties[SaslConfigs.SASL_MECHANISM]).isEqualTo("SCRAM-SHA-512")
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(username)).isEqualTo(true)
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(password)).isEqualTo(true)
    }

    @Test
    fun `Default null protocol config`() {
        val properties = ClusterProperties(
            bootstrapServers,
            securityProtocol = null,
            saslMechanism = null,
            saslUsername = username,
            saslPassword = password
        )

        assertThat(properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(bootstrapServers)
        assertThat(properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo(SecurityProtocol.SASL_SSL.name)
        assertThat(properties[SaslConfigs.SASL_MECHANISM]).isEqualTo("SCRAM-SHA-512")
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(username)).isEqualTo(true)
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(password)).isEqualTo(true)
    }

    @Test
    fun `Default mechanism from constructor`() {
        val properties = ClusterProperties(
            bootstrapServers,
            securityProtocol = null,
            saslMechanism = "Kent",
            saslUsername = username,
            saslPassword = password
        )

        assertThat(properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(bootstrapServers)
        assertThat(properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]).isEqualTo(SecurityProtocol.SASL_SSL.name)
        assertThat(properties[SaslConfigs.SASL_MECHANISM]).isEqualTo("Kent")
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(username)).isEqualTo(true)
        assertThat(properties[SaslConfigs.SASL_JAAS_CONFIG].toString().contains(password)).isEqualTo(true)
    }
}
