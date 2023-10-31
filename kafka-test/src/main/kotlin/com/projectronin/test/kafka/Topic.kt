package com.projectronin.test.kafka

fun topic(topic: String, block: Topic.() -> Unit = {}): Topic = Topic(topic).also {
    block.invoke(it)
}

data class Topic(
    val topic: String,
    val partitions: Int = 1,
    val replication: Int = 1
) {
    val deserializationTypes: MutableMap<String, String> = mutableMapOf()

    override fun toString(): String {
        return topic
    }

    inline fun <reified T : Any> deserializationType(type: String) {
        deserializationTypes[type] = T::class.java.name
    }
}
