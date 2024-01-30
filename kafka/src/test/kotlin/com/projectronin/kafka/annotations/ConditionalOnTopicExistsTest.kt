package com.projectronin.kafka.annotations

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.apache.kafka.common.errors.NetworkException
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationContextException
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*

class ConditionalOnTopicExistsTest {
    private val context = mockk<ConditionContext>(relaxed = true)
    private val metadata = mockk<AnnotatedTypeMetadata>(relaxed = true)
    private val topicExists = spyk<TopicExists>(recordPrivateCalls = true)
    private val properties = Properties().apply {
        this.putAll(
            mapOf(
                "bootstrap-servers" to "localhost:9092",
                "security-protocol" to SecurityProtocol.PLAINTEXT.name.toString()
            )
        )
    }

    private fun propertiesStream(): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        properties.store(output, "")
        return ByteArrayInputStream(output.toByteArray())
    }

    @Test
    fun matches() {
        every { context.resourceLoader.getResource(any()).inputStream } returns propertiesStream()
        every { metadata.annotations.get(ConditionalOnTopicExists::class.java).getValue("topics", Array<String>::class.java) } returns Optional.of(
            arrayOf("test")
        )
        every { topicExists.getActualTopics(any()) } returns mutableSetOf("test")
        val result = topicExists.matches(context, metadata)

        assertThat(result).isTrue()
    }

    @Test
    fun noMatch() {
        every { context.resourceLoader.getResource(any()).inputStream } returns propertiesStream()
        every { metadata.annotations.get(ConditionalOnTopicExists::class.java).getValue("topics", Array<String>::class.java) } returns Optional.of(
            arrayOf("test.other")
        )
        every { topicExists.getActualTopics(any()) } returns mutableSetOf("test")
        val result = topicExists.matches(context, metadata)

        assertThat(result).isFalse()
    }

    @Test
    fun handlesIOExceptions() {
        every { context.resourceLoader.getResource(any()).inputStream } throws IOException("Unable to read file.")

        assertThrows<ApplicationContextException> { topicExists.matches(context, metadata) }
    }

    @Test
    fun handlesKafkaAdminExceptions() {
        every { context.resourceLoader.getResource(any()).inputStream } returns propertiesStream()
        every { metadata.annotations.get(ConditionalOnTopicExists::class.java).getValue("topics", Array<String>::class.java) } returns Optional.of(
            arrayOf("test")
        )
        every { topicExists.getActualTopics(any()) } throws NetworkException("Unable to connect.")

        assertThrows<ApplicationContextException> { topicExists.matches(context, metadata) }
    }
}
