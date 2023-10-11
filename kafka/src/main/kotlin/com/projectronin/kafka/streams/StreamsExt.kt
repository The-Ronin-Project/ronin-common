package com.projectronin.kafka.streams

import com.projectronin.kafka.streams.transformers.MDCTransformerSupplier
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.processor.ProcessorContext

val ProcessorContext.mdc: Map<String, String?>
    get() = mapOf(
        "kafka.topic" to topic().toString(),
        "kafka.partition" to partition().toString(),
        "kafka.offset" to offset().toString()
    )

fun <K, V> StreamsBuilder.eventStream(topic: String?): KStream<K, V> {
    val stream = this.stream<K, V>(topic)
    stream.transform(MDCTransformerSupplier())
    return stream
}
