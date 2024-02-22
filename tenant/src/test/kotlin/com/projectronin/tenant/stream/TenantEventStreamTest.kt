package com.projectronin.tenant.stream

import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.json.tenant.v1.TenantV1Schema
import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventDeserializer
import com.projectronin.kafka.serialization.RoninEventSerializer
import com.projectronin.tenant.config.TenantStreamConfig
import com.projectronin.tenant.handlers.TenantEventHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TenantEventStreamTest {
    private val eventHandler = mockk<TenantEventHandler>()
    private val clusterProperties = ClusterProperties("bootstrap.com:9092", SecurityProtocol.PLAINTEXT)
    private val tenantEventStream = TenantEventStream(
        TenantStreamConfig(
            clusterProperties = clusterProperties,
            applicationId = "application.tenant.v1",
            tenantTopic = "oci.us-phoenix-1.ronin-tenant.tenant.v1",
            dlqTopic = "application.dlq",
            handler = eventHandler
        )
    )

    private val testDriver = TopologyTestDriver(tenantEventStream.topology, tenantEventStream.configs)
    private val inputTopic: TestInputTopic<String, RoninEvent<TenantV1Schema?>> =
        testDriver.createInputTopic("oci.us-phoenix-1.ronin-tenant.tenant.v1", StringSerializer(), RoninEventSerializer())
    private val deserializer = RoninEventDeserializer<TenantV1Schema>()
    private val outputTopic: TestOutputTopic<String?, RoninEvent<TenantV1Schema>> =
        testDriver.createOutputTopic("application.dlq", StringDeserializer(), createDeserializer())

    private fun createDeserializer(): Deserializer<RoninEvent<TenantV1Schema>> {
        val configs = mutableMapOf(
            RoninEventDeserializer.RONIN_DESERIALIZATION_TYPES_CONFIG to "ronin.ronin-tenant.tenant.unknown:${TenantV1Schema::class.qualifiedName},ronin.ronin-tenant.tenant.create:${TenantV1Schema::class.qualifiedName}"
        )
        deserializer.configure(configs, false)

        return deserializer
    }

    private val tenantId = TenantId.random()
    private val tenantSchema = TenantV1Schema().apply {
        this.id = tenantId.value
        this.shortName = "Tenant"
        this.name = "Test Tenant"
        this.type = TenantV1Schema.Type.MOCK_ORACLE
    }

    private val createEvent: RoninEvent<TenantV1Schema?> = RoninEvent(
        dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
        type = "ronin.ronin-tenant.tenant.create",
        source = "ronin-tenant-service",
        resourceId = ResourceId("tenant", tenantId.toString()),
        data = tenantSchema
    )

    private val updateEvent: RoninEvent<TenantV1Schema?> = RoninEvent(
        dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
        type = "ronin.ronin-tenant.tenant.update",
        source = "ronin-tenant-service",
        resourceId = ResourceId("tenant", tenantId.toString()),
        data = tenantSchema
    )

    private val deleteEvent: RoninEvent<TenantV1Schema?> = RoninEvent(
        dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
        type = "ronin.ronin-tenant.tenant.delete",
        source = "ronin-tenant-service",
        resourceId = ResourceId("tenant", tenantId.toString()),
        data = null
    )

    @Test
    fun `create happy path`() {
        val eventSlot = slot<RoninEvent<TenantV1Schema>>()
        every { eventHandler.create(capture(eventSlot)) } just runs
        inputTopic.pipeInput("tenant/$tenantId", createEvent)
        val result = eventSlot.captured
        assertThat(result.data.id).isEqualTo(tenantId.value)
        verify(exactly = 1) { eventHandler.create(any()) }
        assertThat(outputTopic.isEmpty).isTrue()
    }

    @Test
    fun `update happy path`() {
        val eventSlot = slot<RoninEvent<TenantV1Schema>>()
        every { eventHandler.update(capture(eventSlot)) } just runs
        inputTopic.pipeInput("tenant/$tenantId", updateEvent)
        val result = eventSlot.captured
        assertThat(result.data.id).isEqualTo(tenantId.value)
        verify(exactly = 1) { eventHandler.update(any()) }
        assertThat(outputTopic.isEmpty).isTrue()
    }

    @Test
    fun `delete happy path`() {
        val eventSlot = slot<RoninEvent<TenantV1Schema?>>()
        every { eventHandler.delete(capture(eventSlot)) } just runs
        inputTopic.pipeInput("tenant/$tenantId", deleteEvent)
        val result = eventSlot.captured
        assertThat(result.resourceId?.id).isEqualTo(tenantId.value)
        verify(exactly = 1) { eventHandler.delete(any()) }
        assertThat(outputTopic.isEmpty).isTrue()
    }

    @Test
    fun `unknown event type`() {
        val unknownEvent: RoninEvent<TenantV1Schema?> = RoninEvent(
            dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
            type = "ronin.ronin-tenant.tenant.unknown",
            source = "ronin-tenant-service",
            resourceId = ResourceId("tenant", tenantId.toString()),
            data = tenantSchema
        )
        inputTopic.pipeInput("ronin.tenant.tenant.unknown/$tenantId", unknownEvent)
        assertThat(outputTopic.isEmpty).isFalse()
        val dlqRecords = outputTopic.readRecordsToList()
        assertThat(dlqRecords.size).isEqualTo(1)
        assertThat(dlqRecords.first().value.data.id).isEqualTo(tenantId.value)
    }

    @Test
    fun `legacy message type`() {
        val eventSlot = slot<RoninEvent<TenantV1Schema>>()
        every { eventHandler.create(capture(eventSlot)) } just runs
        inputTopic.pipeInput(
            "ronin.tenant.tenant.create/$tenantId",
            RoninEvent(
                dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
                type = "ronin.tenant.tenant.create",
                source = "ronin-tenant-service",
                resourceId = ResourceId("tenant", tenantId.toString()),
                data = tenantSchema
            )
        )
        val result = eventSlot.captured
        assertThat(result.data.id).isEqualTo(tenantId.value)
        verify(exactly = 1) { eventHandler.create(any()) }
        assertThat(outputTopic.isEmpty).isTrue()
    }

    @Test
    fun `exception thrown during handling`() {
        val eventSlot = slot<RoninEvent<TenantV1Schema>>()
        every { eventHandler.create(capture(eventSlot)) } throws Exception("Error happened.")
        inputTopic.pipeInput("tenant/$tenantId", createEvent)
        val result = eventSlot.captured
        assertThat(result.data.id).isEqualTo(tenantId.value)
        verify(exactly = 1) { eventHandler.create(any()) }
        assertThat(outputTopic.isEmpty).isFalse()
        val dlqRecords = outputTopic.readRecordsToList()
        assertThat(dlqRecords.size).isEqualTo(1)
        assertThat(dlqRecords.first().value.data.id).isEqualTo(tenantId.value)
    }
}
