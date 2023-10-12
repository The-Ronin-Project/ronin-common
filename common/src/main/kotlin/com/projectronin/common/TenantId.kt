package com.projectronin.common

import java.security.SecureRandom

@JvmInline
value class TenantId(val value: String) {
    override fun toString() = value

    init {
        require(value in nonstandardIds || base32Regex.matches(value)) { "invalid tenant id: $value" }
    }

    companion object {
        /**
         * https://projectronin.atlassian.net/wiki/spaces/ENG/pages/1737556005/Organization+Ids
         * Base32: https://www.crockford.com/base32.html
         */
        fun random() = TenantId(
            buildString {
                repeat(8) {
                    val index = random.nextInt(base32Alphabet.length)
                    append(base32Alphabet[index])
                }
            }
        )

        val apposnd = TenantId("apposnd")
        val ronin = TenantId("ronin")
        val mdaoc = TenantId("mdaoc")
    }
}

private val nonstandardIds = listOf("apposnd", "mdaoc", "ronin")
private const val base32Alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
private val base32Regex = Regex("^[$base32Alphabet]{8}$")
private val random = SecureRandom()
