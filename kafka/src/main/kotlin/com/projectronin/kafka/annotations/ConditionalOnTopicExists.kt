package com.projectronin.kafka.annotations

import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.spring.config.KafkaConfiguration
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.springframework.context.ApplicationContextException
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Import
import org.springframework.core.type.AnnotatedTypeMetadata
import java.io.IOException
import java.util.*

@Target(AnnotationTarget.CLASS)
@Repeatable
@Conditional(TopicExists::class)
annotation class ConditionalOnTopicExists(vararg val topics: String)

@Import(KafkaConfiguration::class)
class TopicExists : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        try {
            val props = Properties()
            props.load(context.resourceLoader.getResource("classpath:application.yml").inputStream)
            val bootstrapServers: String = props.getProperty("bootstrap-servers")
            val securityProtocol: SecurityProtocol? = SecurityProtocol.forName(props.getProperty("security-protocol"))
            val saslMechanism: String? = props.getProperty("sasl-mechanism")
            val saslUsername: String? = props.getProperty("sasl-username")
            val saslPassword: String? = props.getProperty("sasl-password")

            val clusterProperties = ClusterProperties(bootstrapServers, securityProtocol, saslMechanism, saslUsername, saslPassword)

            val topics = metadata.annotations.get(ConditionalOnTopicExists::class.java)
                .getValue("topics", Array<String>::class.java)
                .orElseThrow { RuntimeException("@ConditionalOnTopicExists requires topics, none were provided.") }

            runCatching {
                return@runCatching getActualTopics(clusterProperties)
            }.onSuccess {
                return it.containsAll(topics.asList())
            }.onFailure {
                it.printStackTrace()
                throw ApplicationContextException("Kafka Admin Exception while loading context", it)
            }
        } catch (e: IOException) {
            throw ApplicationContextException("Exception while loading context", e)
        }

        return false
    }

    fun getActualTopics(clusterProperties: ClusterProperties): MutableSet<String> {
        val adminClient = Admin.create(clusterProperties)
        val kafkaTopics = adminClient.listTopics().names().get()
        adminClient.close()
        return kafkaTopics
    }
}
