package com.projectronin.kafka.streams.transformers

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.common.telemetry.Tags.KAFKA_OFFSET_TAG
import com.projectronin.common.telemetry.Tags.KAFKA_PARTITION_TAG
import com.projectronin.common.telemetry.Tags.KAFKA_TOPIC_TAG
import com.projectronin.common.telemetry.Tags.RONIN_EVENT_ID_TAG
import com.projectronin.common.telemetry.Tags.RONIN_EVENT_RESOURCE_ID_TAG
import com.projectronin.common.telemetry.Tags.RONIN_EVENT_RESOURCE_TYPE_TAG
import com.projectronin.common.telemetry.Tags.RONIN_EVENT_TYPE_TAG
import com.projectronin.common.telemetry.Tags.RONIN_EVENT_VERSION_TAG
import com.projectronin.common.telemetry.Tags.TENANT_TAG
import com.projectronin.kafka.data.RoninEvent
import io.mockk.mockkStatic
import io.mockk.verify
import org.apache.kafka.streams.processor.MockProcessorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.UUID

class MDCTransformerTest {
    private data class Foo(val id: String)

    @Test
    fun `transform adds MDC`() {
        mockkStatic(MDC::class)

        val transformer = MDCTransformer<String, RoninEvent<Foo>>()
        val context = MockProcessorContext()

        context.setTopic("test-topic")
        context.setPartition(14)
        context.setOffset(45)

        transformer.init(context)

        val id = UUID.randomUUID()

        val event = RoninEvent(
            id = id,
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "ronin.ehr.document-reference.create",
            data = Foo("carl was here"),
            tenantId = TenantId("apposnd"),
            patientId = PatientId("patient123"),
            resourceId = ResourceId("resourceType", "resourceId")
        )

        transformer.transform("test/123", event)
        verify {
            MDC.setContextMap(
                withArg { tags ->
                    assertThat(tags[KAFKA_TOPIC_TAG]).isEqualTo("test-topic")
                    assertThat(tags[KAFKA_PARTITION_TAG]).isEqualTo("14")
                    assertThat(tags[KAFKA_OFFSET_TAG]).isEqualTo("45")

                    assertThat(tags[RONIN_EVENT_ID_TAG]).isEqualTo(id.toString())
                    assertThat(tags[RONIN_EVENT_VERSION_TAG]).isEqualTo("2")
                    assertThat(tags[RONIN_EVENT_TYPE_TAG]).isEqualTo("ronin.ehr.document-reference.create")
                    assertThat(tags[TENANT_TAG]).isEqualTo("apposnd")
                    assertThat(tags[RONIN_EVENT_RESOURCE_TYPE_TAG]).isEqualTo("resourceType")
                    assertThat(tags[RONIN_EVENT_RESOURCE_ID_TAG]).isEqualTo("resourceId")
                }
            )
        }
        transformer.close()
    }

    @Test
    fun `supplier creates transformer`() {
        val supplier = MDCTransformerSupplier<String, RoninEvent<Foo>>()
        val transformer = supplier.get()
        assertThat(transformer).isNotNull
    }
}
