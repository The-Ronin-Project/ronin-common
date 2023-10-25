package com.projectronin.kafka.streams

import com.projectronin.kafka.streams.transformers.MDCTransformerSupplier
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.ProcessorContext

val ProcessorContext.mdc: Map<String, String?>
    get() = mapOf(
        "kafka.topic" to topic().toString(),
        "kafka.partition" to partition().toString(),
        "kafka.offset" to offset().toString()
    )

fun <K, V> StreamsBuilder.eventStream(topic: String?): KStream<K, V> {
    val stream = this.stream<K, V>(topic)
    stream.transform(MDCTransformerSupplier(), Named.`as`("MDC_TRANSFORMER"))
    return stream
}

fun <K, V> stream(topic: String, block: (KStream<K, V>) -> Unit): Topology {
    require(topic.isNotBlank()) { "topic cannot be empty string" }
    return stream(listOf(topic), block)
}

fun <K, V> stream(topics: List<String>, block: (KStream<K, V>) -> Unit): Topology {
    require(topics.isNotEmpty()) { "topics cannot be empty list" }
    require(topics.all { t -> t.isNotBlank() }) { "topics must contain at least one valid topic" }
    val builder = StreamsBuilder()
    val kStream: KStream<K, V> = builder.stream(topics)
    kStream.transform(MDCTransformerSupplier(), Named.`as`("MDC_TRANSFORMER"))
    block.invoke(kStream)
    return builder.build()
}
