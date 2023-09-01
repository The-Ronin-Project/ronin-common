package com.projectronin.common

data class Resource(
    val resourceType: String,
    val resourceId: String
) {
    override fun toString(): String {
        return "${this.resourceType}/${this.resourceId}"
    }

    companion object {
        fun parse(value: String?): Resource? {
            return when (value) {
                null -> null
                else -> {
                    val strings = value.split("/")
                    when (strings.size) {
                        2 -> {
                            Resource(strings[0], strings[1])
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
