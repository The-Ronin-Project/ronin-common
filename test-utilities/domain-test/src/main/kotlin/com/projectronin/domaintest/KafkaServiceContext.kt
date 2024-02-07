package com.projectronin.domaintest

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.util.Properties

class KafkaServiceContext private constructor(private val network: Network) : DomainTestContainerContext {

    companion object {
        internal fun createInstance(network: Network): KafkaServiceContext {
            if (_instance == null) {
                _instance = KafkaServiceContext(network)
            }
            return _instance!!
        }

        private var _instance: KafkaServiceContext? = null
        internal val instance: KafkaServiceContext
            get() = _instance ?: throw IllegalStateException("No Kafka service has been configured")
    }

    private val topics = mutableSetOf<Topic>()

    internal val topicNames: Set<String>
        get() = topics.map { it.name }.toSet()

    private var _container: KafkaContainer? = null
    private val container: KafkaContainer
        get() = _container ?: throw IllegalStateException("Kafka service has not been started")

    init {
        _instance = this
    }

    fun topic(name: String, partitions: Int = 1, replication: Int = 1) {
        topics += Topic(name, partitions, replication)
    }

    fun topics(vararg name: String) {
        name.forEach { topic(it) }
    }

    override fun createContainer(): GenericContainer<*> {
        if (_container == null) {
            _container = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                .withNetwork(network)
                .withNetworkAliases(DomainTestSetupContext.kafkaContainerName)
                .withListener { "kafka:19092" }
                // .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9093,BROKER://0.0.0.0:9092,INTERNAL://kafka:19092")
                // .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,INTERNAL:PLAINTEXT")
                // .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:9093,BROKER://localhost:9092,INTERNAL://kafka:19092")
                .withExposedPorts(9093, 19092)
        }
        return container
    }

    override fun bootstrap(container: GenericContainer<*>) {
        val newTopics = topics.map { NewTopic(it.name, it.partitions, it.replication.toShort()) }
            .takeUnless { it.isEmpty() }
            ?: return

        withAdminClient { createTopics(newTopics) }
    }

    internal fun withAdminClient(block: AdminClient.() -> Unit) {
        createAdminClient().use { block(it) }
    }

    internal val port: Int
        get() = container.getMappedPort(9093)

    internal val host: String
        get() = container.host

    internal val bootstrapServers: String
        get() = container.bootstrapServers

    private fun createAdminClient() = AdminClient.create(
        Properties().apply {
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaExternalBootstrapServers
            this[CommonClientConfigs.CLIENT_ID_CONFIG] = "tc-admin-client"
        }
    )

    private data class Topic(
        val name: String,
        val partitions: Int = 1,
        val replication: Int = 1
    )
}

fun withKafkaAdminClient(block: AdminClient.() -> Unit) {
    KafkaServiceContext.instance.withAdminClient(block)
}

val kafkaPort: Int
    get() = KafkaServiceContext.instance.port
val kafkaExternalHost: String
    get() = KafkaServiceContext.instance.host
val kafkaExternalBootstrapServers: String
    get() = KafkaServiceContext.instance.bootstrapServers
val kafkaInternalBootstrapServers: String
    get() = "PLAINTEXT://kafka:19092"

val registeredTopics: Set<String>
    get() = KafkaServiceContext.instance.topicNames
