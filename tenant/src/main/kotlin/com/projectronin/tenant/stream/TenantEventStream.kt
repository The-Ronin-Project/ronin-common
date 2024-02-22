package com.projectronin.tenant.stream

import com.projectronin.common.telemetry.addToDDTraceSpan
import com.projectronin.json.tenant.v1.TenantV1Schema
import com.projectronin.kafka.config.StreamProperties
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.handlers.DeadLetterDeserializationExceptionHandler
import com.projectronin.kafka.streams.kafkaStreams
import com.projectronin.kafka.streams.stream
import com.projectronin.tenant.config.TenantStreamConfig
import kotlinx.coroutines.runBlocking
import mu.KLogger
import mu.KotlinLogging
import org.apache.kafka.streams.Topology

class TenantEventStream(
    private val tenantStreamConfig: TenantStreamConfig
) {
    private val logger: KLogger = KotlinLogging.logger { }
    val topology = buildTopology()

    val configs = StreamProperties(tenantStreamConfig.clusterProperties, tenantStreamConfig.applicationId) {
        addDeserializationType<TenantV1Schema>("ronin.tenant.tenant.")
        addDeserializationType<TenantV1Schema>("ronin.ronin-tenant.tenant.")
        put(DeadLetterDeserializationExceptionHandler.DEAD_LETTER_TOPIC_CONFIG, tenantStreamConfig.dlqTopic)
    }

    fun initialize() = kafkaStreams(topology, configs).start()

    private fun buildTopology(): Topology {
        return stream<String, RoninEvent<TenantV1Schema?>>(tenantStreamConfig.tenantTopic) { kStream ->
            kStream.peek { k, v -> logger.info { "Receiving Tenant message ${v.type}: $k" } }
                .flatMapValues { v ->
                    return@flatMapValues when {
                        handle(v).isFailure -> listOf(v)
                        else -> listOf()
                    }
                }
                .to(tenantStreamConfig.dlqTopic)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handle(
        command: RoninEvent<TenantV1Schema?>
    ) = runBlocking {
        runCatching {
            when (command.type.split(".").last()) {
                "create" -> tenantStreamConfig.handler.create(command as RoninEvent<TenantV1Schema>)
                "update" -> tenantStreamConfig.handler.update(command as RoninEvent<TenantV1Schema>)
                "delete" -> tenantStreamConfig.handler.delete(command)
                else -> throw Exception("Unknown Tenant event type ${command.type}.")
            }
        }
            .onSuccess {
                val action = command.type.split(".").last().replaceFirstChar { it.uppercase() }
                logger.info { "${action}d tenant ${command.resourceId?.id}." }
            }
            .onFailure {
                it.addToDDTraceSpan()
                logger.warn(it) { "Unable to process Tenant event. ${it.message}" }
            }
    }
}
