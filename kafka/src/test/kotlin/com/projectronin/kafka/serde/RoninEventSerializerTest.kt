package com.projectronin.kafka.serde

import com.projectronin.common.Resource
import com.projectronin.common.ResourceType
import com.projectronin.common.Services
import com.projectronin.kafka.data.RoninEvent
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RoninEventSerializerTest {
    private data class Foo(val bar: String)
    private val serializer = RoninEventSerializer<Foo>()

    private val testId: UUID? = UUID.fromString("350e8400-e29b-41d4-a716-000000000000")

    @BeforeEach
    fun `Setup UUID mocks`() {
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.returns(testId)
    }

    @AfterEach
    fun `Remove UUID mockks`() {
        unmockkStatic(UUID::class)
    }

    @Test
    fun `Test Serialize Null`() {
        assertThat(serializer.serialize("topic", RecordHeaders(), null)).isNull()
    }

    @Test
    fun `Test Serialize Without Headers`() {
        assertThatThrownBy { serializer.serialize("topic", null) }
            .isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `Test serialize default values`() {
        val event = RoninEvent(
            source = Services.ASSETS,
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "${ResourceType.RONIN_DOCUMENT_REFERENCE}.create",
            data = Foo("carl was here"),
            tenantId = "apposnd",
            patientId = "patient123",
            resource = Resource("resourceType", "resourceId")
        )

        val headers = RecordHeaders()
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ce_id")).isEqualTo(testId.toString())
        assertThat(headers.get("ce_source")).isEqualTo(Services.ASSETS)
        assertThat(headers.get("ce_specversion")).isEqualTo("2")
        assertThat(headers.get("ce_type")).isEqualTo("${ResourceType.RONIN_DOCUMENT_REFERENCE}.create")
        assertThat(headers.get("content-type")).isEqualTo("application/json")
        assertThat(headers.get("ce_dataschema")).isEqualTo("http://schemas/asset")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("apposnd")
        assertThat(headers.get("ronin_patient_id")).isEqualTo("patient123")
        assertThat(headers.get("ce_subject")).isEqualTo("resourceType/resourceId")
    }

    @Test
    fun `Test serialize backwards to RoninWrapper`() {
        val event = RoninEvent(
            source = Services.ASSETS,
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "${ResourceType.RONIN_DOCUMENT_REFERENCE}.create",
            data = Foo("carl was here"),
            tenantId = "apposnd",
            patientId = "patient123",
            resource = Resource("resourceType", "resourceId")
        )

        val headers = RecordHeaders()
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ronin_wrapper_version")).isEqualTo("2")
        assertThat(headers.get("ronin_source_service")).isEqualTo(Services.ASSETS)
        assertThat(headers.get("ronin_data_type")).isEqualTo("${ResourceType.RONIN_DOCUMENT_REFERENCE}.create")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("apposnd")
    }

    @Test
    fun `Test serialize backwards to RoninWrapper no Tenant`() {
        val event = RoninEvent(
            source = Services.ASSETS,
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "${ResourceType.RONIN_DOCUMENT_REFERENCE}.create",
            data = Foo("carl was here")
        )

        val headers = RecordHeaders()
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ronin_wrapper_version")).isEqualTo("2")
        assertThat(headers.get("ronin_source_service")).isEqualTo(Services.ASSETS)
        assertThat(headers.get("ronin_data_type")).isEqualTo("${ResourceType.RONIN_DOCUMENT_REFERENCE}.create")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("unknown")
    }
}
