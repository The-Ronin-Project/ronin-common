package com.projectronin.kafka.serde

import com.fasterxml.jackson.core.JsonParseException
import com.projectronin.kafka.data.RoninEventHeaders
import com.projectronin.kafka.data.StringHeader
import com.projectronin.kafka.exceptions.ConfigurationException
import com.projectronin.kafka.exceptions.EventHeaderMissing
import com.projectronin.kafka.exceptions.UnknownEventType
import com.projectronin.kafka.serde.RoninEventDeserializer.Companion.RONIN_DESERIALIZATION_TYPES_CONFIG
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class RoninEventDeserializerTest {
    private data class Stuff(val id: String)

    private val fixedInstant: Instant = Instant.ofEpochSecond(1660000000)
    private val testId: UUID? = UUID.fromString("350e8400-e29b-41d4-a716-000000000000")
    private val typeValue = "stuff:com.projectronin.kafka.serde.RoninEventDeserializerTest\$Stuff"

    @Test
    fun `deserialize no headers error`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)
        assertThatThrownBy {
            deserializer.deserialize("topic", "MattersNot".toByteArray())
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `deserialize with complete headers`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)
        val headers = RecordHeaders(
            mutableListOf(
                StringHeader(RoninEventHeaders.ID, testId.toString()),
                StringHeader(RoninEventHeaders.SOURCE, "test"),
                StringHeader(RoninEventHeaders.VERSION, "2"),
                StringHeader(RoninEventHeaders.TYPE, "stuff"),
                StringHeader(RoninEventHeaders.CONTENT_TYPE, "content"),
                StringHeader(RoninEventHeaders.DATA_SCHEMA, "schema"),
                StringHeader(RoninEventHeaders.TIME, "2022-08-08T23:06:40Z"),
                StringHeader(RoninEventHeaders.SUBJECT, "stuff/3")
            )
        )
        val event = deserializer.deserialize("topic", headers, "{\"id\":\"3\"}".encodeToByteArray())

        assertThat(event).isNotNull
        assertThat(event.id).isEqualTo(testId)
        assertThat(event.source).isEqualTo("test")
        assertThat(event.version).isEqualTo("2")
        assertThat(event.type).isEqualTo("stuff")
        assertThat(event.dataContentType).isEqualTo("content")
        assertThat(event.dataSchema).isEqualTo("schema")
        assertThat(event.time).isEqualTo(fixedInstant)
        assertThat(event.data).isEqualTo(Stuff("3"))
    }

    @Test
    fun `deserialize missing required headers error`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)
        assertThatThrownBy {
            val headers = RecordHeaders(
                mutableListOf(
                    StringHeader(RoninEventHeaders.ID, testId.toString()),
                    StringHeader(RoninEventHeaders.SOURCE, "test"),
                    StringHeader(RoninEventHeaders.VERSION, "4.2"),
                    StringHeader(RoninEventHeaders.CONTENT_TYPE, "content"),
                    StringHeader(RoninEventHeaders.DATA_SCHEMA, "schema"),
                    StringHeader(RoninEventHeaders.TIME, "2022-08-08T23:06:40Z"),
                    StringHeader(RoninEventHeaders.SUBJECT, "stuff.3")
                )
            )
            deserializer.deserialize("topic", headers, "MattersNot".toByteArray())
        }.isInstanceOf(EventHeaderMissing::class.java)
    }

    @Test
    fun `deserialize missing type in map error`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(
            mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to "foor:${Stuff::class.java.name}"),
            false
        )
        assertThatThrownBy {
            val headers = RecordHeaders(
                mutableListOf(
                    StringHeader(RoninEventHeaders.ID, testId.toString()),
                    StringHeader(RoninEventHeaders.SOURCE, "test"),
                    StringHeader(RoninEventHeaders.VERSION, "4.2"),
                    StringHeader(RoninEventHeaders.TYPE, "stuff"),
                    StringHeader(RoninEventHeaders.CONTENT_TYPE, "content"),
                    StringHeader(RoninEventHeaders.DATA_SCHEMA, "schema"),
                    StringHeader(RoninEventHeaders.TIME, "2022-08-08T23:06:40Z"),
                    StringHeader(RoninEventHeaders.SUBJECT, "stuff.3")
                )
            )
            deserializer.deserialize("topic", headers, "MattersNot".toByteArray())
        }.isInstanceOf(UnknownEventType::class.java)
    }

    @Test
    fun `deserialize bad data error`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)
        assertThatThrownBy {
            val headers = RecordHeaders(
                mutableListOf(
                    StringHeader(RoninEventHeaders.ID, testId.toString()),
                    StringHeader(RoninEventHeaders.SOURCE, "test"),
                    StringHeader(RoninEventHeaders.VERSION, "4.2"),
                    StringHeader(RoninEventHeaders.TYPE, "stuff"),
                    StringHeader(RoninEventHeaders.CONTENT_TYPE, "content"),
                    StringHeader(RoninEventHeaders.DATA_SCHEMA, "schema"),
                    StringHeader(RoninEventHeaders.TIME, "2022-08-08T23:06:40Z"),
                    StringHeader(RoninEventHeaders.SUBJECT, "stuff.3")
                )
            )
            deserializer.deserialize("topic", headers, "MattersNot".toByteArray())
        }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun `deserialize ronin wrapper v1`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)

        val headers = RecordHeaders(
            mutableListOf(
                StringHeader("ronin_source_service", "test"),
                StringHeader("ronin_wrapper_version", "1.0"),
                StringHeader("ronin_tenant_id", "apposnd"),
                StringHeader("ronin_data_type", "stuff")
            )
        )
        val event = deserializer.deserialize("topic", headers, "{\"id\":\"3\"}".encodeToByteArray())

        assertThat(event).isNotNull
        assertThat(event.id).isEqualTo(UUID(0, 0))
        assertThat(event.source).isEqualTo("test")
        assertThat(event.version).isEqualTo("1.0")
        assertThat(event.type).isEqualTo("stuff")
        assertThat(event.dataContentType).isEqualTo("application/json")
        assertThat(event.dataSchema).isEqualTo("unknown")
        assertThat(event.data).isEqualTo(Stuff("3"))
    }

    @Test
    fun `deserialize with no version`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        deserializer.configure(mutableMapOf(RONIN_DESERIALIZATION_TYPES_CONFIG to typeValue), false)
        assertThatThrownBy {
            val headers = RecordHeaders(
                mutableListOf(
                    StringHeader(RoninEventHeaders.ID, testId.toString()),
                    StringHeader(RoninEventHeaders.SOURCE, "test"),
                    StringHeader(RoninEventHeaders.TYPE, "stuff"),
                    StringHeader(RoninEventHeaders.CONTENT_TYPE, "content"),
                    StringHeader(RoninEventHeaders.DATA_SCHEMA, "schema"),
                    StringHeader(RoninEventHeaders.TIME, "2022-08-08T23:06:40Z"),
                    StringHeader(RoninEventHeaders.SUBJECT, "stuff.3")
                )
            )
            deserializer.deserialize("topic", headers, "MattersNot".toByteArray())
        }.isInstanceOf(EventHeaderMissing::class.java)
    }

    @Test
    fun `deserialize with no type map config`() {
        val deserializer = RoninEventDeserializer<Stuff>()
        assertThatThrownBy {
            deserializer.configure(mutableMapOf<String, Any?>(), false)
        }.isInstanceOf(ConfigurationException::class.java)
    }
}
