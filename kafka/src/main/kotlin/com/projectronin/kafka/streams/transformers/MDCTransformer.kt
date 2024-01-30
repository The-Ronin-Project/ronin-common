package com.projectronin.kafka.streams.transformers

import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.streams.mdc
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Transformer
import org.apache.kafka.streams.kstream.TransformerSupplier
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.MDC

class MDCTransformerSupplier<K, V> : TransformerSupplier<K, V, KeyValue<K, V>> {
    override fun get(): Transformer<K, V, KeyValue<K, V>> {
        return MDCTransformer()
    }
}

class MDCTransformer<K, V> : Transformer<K, V, KeyValue<K, V>> {
    private var context: ProcessorContext? = null

    override fun init(context: ProcessorContext?) {
        this.context = context
    }

    override fun transform(key: K?, value: V?): KeyValue<K, V> {
        val contextMdc = context?.mdc ?: emptyMap()
        val eventMdc = (value as? RoninEvent<*>)?.mdc ?: emptyMap()

        MDC.setContextMap(contextMdc + eventMdc)

        return KeyValue(key, value)
    }

    override fun close() {
        // Nothing to do here
    }
}
