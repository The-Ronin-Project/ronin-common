package com.projectronin.common

import mu.KLogger
import mu.KotlinLogging

data class ResourceId(
    val type: String,
    val id: String
) {
    private val logger: KLogger = KotlinLogging.logger { }

    init {
        if (type.contains(".")) logger.warn("Resource.type should not contain '.'")

        require(!type.contains("/")) { "Resource.type can not contain '/'" }
        require(!id.contains("/")) { "Resource.id can not contain '/'" }
    }

    override fun toString(): String {
        return "${this.type}/${this.id}"
    }

    companion object {
        fun parseOrNull(value: String?): ResourceId? {
            return when (value) {
                null -> null
                else -> {
                    val strings = value.split("/")
                    when (strings.size) {
                        2 -> ResourceId(strings[0], strings[1])
                        else -> throw IllegalArgumentException("Improper format for Resource value")
                    }
                }
            }
        }
    }
}
