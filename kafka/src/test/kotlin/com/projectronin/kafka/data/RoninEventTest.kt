package com.projectronin.kafka.data

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.common.telemetry.Tags
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_CONTENT_TYPE
import com.projectronin.kafka.data.RoninEvent.Companion.DEFAULT_VERSION
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

class RoninEventTest {
    private data class Foo(val bar: String)

    @Test
    fun `Create RoninEvent validate default values`() {
        val testId = UUID.fromString("350e8400-e29b-41d4-a716-000000000000")
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.returns(testId)

        val event = RoninEvent<Foo>(
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "something.create"
        )

        assertThat(event.id).isEqualTo(testId)
        assertThat(Instant.now().isAfter(event.time)).isTrue()
        assertThat(event.version).isEqualTo(DEFAULT_VERSION)
        assertThat(event.dataContentType).isEqualTo(DEFAULT_CONTENT_TYPE)
    }

    @Test
    fun `RoninEvent ResourceIds with dots and dashes`() {
        val id = "ronin-1PEeQK.QWzOjRADXBY0-TdBOkElsK.jQc7huXbQ-ou0Ga"

        assertThat(ResourceId.fromHeaderOrNull("asset/$id")?.type).isEqualTo("asset")
        assertThat(ResourceId.fromHeaderOrNull("asset/$id")?.id).isEqualTo(id)
        assertThat(ResourceId.fromHeaderOrNull("ronin.prodeng-assets.asset/$id")?.type).isEqualTo("asset")
        assertThat(ResourceId.fromHeaderOrNull("ronin.prodeng-assets.asset/$id")?.id).isEqualTo("ronin-1PEeQK.QWzOjRADXBY0-TdBOkElsK.jQc7huXbQ-ou0Ga")

        assertThrows<IllegalArgumentException> {
            ResourceId.fromHeaderOrNull("ronin/prodeng-assets/asset/$id")
        }
        assertThrows<IllegalArgumentException> {
            ResourceId.fromHeaderOrNull("ronin.prodeng-assets.asset/1234/5678")
        }
    }

    @Test
    fun `mdc creates a map`() {
        val testId = UUID.fromString("350e8400-e29b-41d4-a716-000000000000")
        val event = RoninEvent<Foo>(
            version = "3",
            id = testId,
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "something.create",
            patientId = PatientId("patient123"),
            tenantId = TenantId("apposnd")
        )
        val mdc = event.mdc
        assertThat(mdc[Tags.RONIN_EVENT_ID_TAG]).isEqualTo(testId.toString())
        assertThat(mdc[Tags.RONIN_EVENT_TYPE_TAG]).isEqualTo("something.create")
        assertThat(mdc[Tags.RONIN_EVENT_VERSION_TAG]).isEqualTo("3")
        assertThat(mdc[Tags.TENANT_TAG]).isEqualTo("apposnd")
    }

    @AfterEach
    fun `Remove UUID mockks`() {
        clearAllMocks()
        unmockkStatic(UUID::class)
    }
}
