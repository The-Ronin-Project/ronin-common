package com.projectronin.common

data class ResourceId(
    val type: String,
    val id: String
) {
    override fun toString(): String {
        return "${this.type}/${this.id}"
    }

    companion object {
        fun parse(value: String?): ResourceId? {
            return when (value) {
                null -> null
                else -> {
                    val strings = value.split("/")
                    when (strings.size) {
                        2 -> {
                            ResourceId(strings[0], strings[1])
                        }

                        else -> {
                            throw IllegalArgumentException("Improper format for Resource value")
                        }
                    }
                }
            }
        }
    }
}
