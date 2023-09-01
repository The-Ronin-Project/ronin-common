package com.projectronin.kafka.exceptions

class EventHeaderMissing(missingHeaders: List<String>, val topic: String? = null) : RuntimeException(
    "Unable to process event. The following headers are required: ${missingHeaders.joinToString(", ")}"
)

class UnknownEventType(val key: String? = null, val type: String?, val topic: String? = null) :
    RuntimeException("No processor found for event type `$type`")

class ConfigurationException(override val message: String) :
    RuntimeException(message)
