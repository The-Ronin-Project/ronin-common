package com.projectronin.kafka.serialization

import com.projectronin.common.PatientId
import com.projectronin.common.ResourceId
import com.projectronin.common.TenantId
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.data.RoninEventHeaders
import com.projectronin.kafka.data.StringHeader
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.*

class RoninEventSerializerTest {
    private data class Foo(val bar: String)

    private val serializer = RoninEventSerializer<Foo>()

    @AfterEach
    fun `Remove UUID mockks`() {
        clearAllMocks()
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
        val testId = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns testId

        val event = RoninEvent(
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "ronin.ehr.document-reference.create",
            data = Foo("carl was here"),
            tenantId = TenantId("apposnd"),
            patientId = PatientId("patient123"),
            resourceId = ResourceId("resourceType", "resourceId")
        )

        val headers = RecordHeaders()
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ce_id")).isEqualTo(testId.toString())
        assertThat(headers.get("ce_source")).isEqualTo("prodeng-assets")
        assertThat(headers.get("ce_specversion")).isEqualTo("2")
        assertThat(headers.get("ce_type")).isEqualTo("ronin.ehr.document-reference.create")
        assertThat(headers.get("content-type")).isEqualTo("application/json")
        assertThat(headers.get("ce_dataschema")).isEqualTo("http://schemas/asset")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("apposnd")
        assertThat(headers.get("ronin_patient_id")).isEqualTo("patient123")
        assertThat(headers.get("ce_subject")).isEqualTo("resourceType/resourceId")
    }

    @Test
    fun `Test serialize backwards to RoninWrapper`() {
        val event = RoninEvent(
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "ronin.ehr.document-reference.create",
            data = Foo("carl was here"),
            tenantId = TenantId("apposnd"),
            patientId = PatientId("patient123"),
            resourceId = ResourceId("resourceType", "resourceId")
        )

        val headers = RecordHeaders()
        serializer.configure(
            mutableMapOf(
                RoninEventSerializer.RONIN_SERIALIZE_LEGACY_CONFIG to "TEST,WRAPPER,DUMMY"
            ),
            false
        )
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ronin_wrapper_version")).isEqualTo("2")
        assertThat(headers.get("ronin_source_service")).isEqualTo("prodeng-assets")
        assertThat(headers.get("ronin_data_type")).isEqualTo("ronin.ehr.document-reference.create")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("apposnd")
    }

    @Test
    fun `Test serialize backwards to RoninWrapper no Tenant`() {
        val event = RoninEvent(
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "ronin.ehr.document-reference.create",
            data = Foo("carl was here")
        )

        val headers = RecordHeaders()
        serializer.configure(
            mutableMapOf(
                RoninEventSerializer.RONIN_SERIALIZE_LEGACY_CONFIG to "WRAPPER"
            ),
            false
        )
        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ronin_wrapper_version")).isEqualTo("2")
        assertThat(headers.get("ronin_source_service")).isEqualTo("prodeng-assets")
        assertThat(headers.get("ronin_data_type")).isEqualTo("ronin.ehr.document-reference.create")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("unknown")
    }

    @Test
    fun `Test serialize clears previous headers`() {
        val testId = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns testId

        val event = RoninEvent(
            source = "prodeng-assets",
            dataContentType = "application/json",
            dataSchema = "http://schemas/asset",
            type = "ronin.ehr.document-reference.create",
            data = Foo("carl was here"),
            tenantId = TenantId("apposnd"),
            patientId = PatientId("patient123"),
            resourceId = ResourceId("resourceType", "resourceId")
        )

        val headers = RecordHeaders(
            mutableListOf(
                StringHeader(RoninEventHeaders.ID, UUID(0, 0).toString()),
                StringHeader(RoninEventHeaders.SOURCE, "asdf"),
                StringHeader(RoninEventHeaders.VERSION, "2asdf"),
                StringHeader(RoninEventHeaders.TYPE, "asdf.create"),
                StringHeader(RoninEventHeaders.CONTENT_TYPE, "asdf"),
                StringHeader(RoninEventHeaders.DATA_SCHEMA, "asdf"),
                StringHeader(RoninEventHeaders.TIME, "2022-asdf-08T23:06:40Z"),
                StringHeader(RoninEventHeaders.SUBJECT, "asdfasdfasdf3"),
                StringHeader(RoninEventHeaders.TENANT_ID, "asdf"),
                StringHeader(RoninEventHeaders.PATIENT_ID, "somePatieasdfasdfasdfasdfntId"),
                StringHeader("ronin_source_service", "tesfdsfdt"),
                StringHeader("ronin_wrapper_version", "1dfdf"),
                StringHeader("ronin_tenant_id", "apposfdnd"),
                StringHeader("ronin_data_type", "stuff.creaasdfte")
            )
        )

        val bytes = serializer.serialize("topic", headers, event)

        assertThat(bytes?.decodeToString()).isEqualTo("{\"bar\":\"carl was here\"}")

        assertThat(headers.get("ce_id")).isEqualTo(testId.toString())
        assertThat(headers.get("ce_source")).isEqualTo("prodeng-assets")
        assertThat(headers.get("ce_specversion")).isEqualTo("2")
        assertThat(headers.get("ce_type")).isEqualTo("ronin.ehr.document-reference.create")
        assertThat(headers.get("content-type")).isEqualTo("application/json")
        assertThat(headers.get("ce_dataschema")).isEqualTo("http://schemas/asset")
        assertThat(headers.get("ronin_tenant_id")).isEqualTo("apposnd")
        assertThat(headers.get("ronin_patient_id")).isEqualTo("patient123")
        assertThat(headers.get("ce_subject")).isEqualTo("resourceType/resourceId")
        assertThat(headers.get("ronin_wrapper_version")).isNull()
        assertThat(headers.get("ronin_source_service")).isNull()
        assertThat(headers.get("ronin_data_type")).isNull()
    }
}
