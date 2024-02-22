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
import org.apache.kafka.streams.kstream.Named

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
            kStream.filter({ _, value -> value != null }, Named.`as`("FILTER_NULL"))
                .peek { k, v -> logger.info { "Receiving Tenant message ${v.resourceId?.type}. $k: ${v.resourceId?.id}" } }
                .flatMapValues { v ->
                    return@flatMapValues when {
                        handle(v).isFailure -> listOf(v)
                        else -> listOf()
                    }
                }
                .to(tenantStreamConfig.dlqTopic)
        }
    }

    private fun handle(
        command: RoninEvent<TenantV1Schema?>
    ) = runBlocking {
        runCatching {
            when (command.type.split(".").last()) {
                "create" -> tenantStreamConfig.handler.create(command)
                "update" -> tenantStreamConfig.handler.update(command)
                "delete" -> tenantStreamConfig.handler.delete(command)
                else -> throw Exception("Unknown Tenant event type ${command.type}.")
            }
        }
            .onSuccess {
                val action = command.type.split(".").last().replaceFirstChar { it.uppercase() }
                logger.info { "${action}d tenant ${command.tenantId}." }
            }
            .onFailure {
                it.addToDDTraceSpan()
                logger.warn { "Unable to process Tenant event. ${it.message}" }
            }
    }
}
