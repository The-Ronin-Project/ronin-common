package com.projectronin.test.jwt

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Holds a simple Jackson instance for use encoding and decoding token instances in tests.
 */
internal object JwtAuthTestJackson {
    internal val objectMapper: ObjectMapper by lazy {
        val om = ObjectMapper()
        om.findAndRegisterModules()
        om
    }
}
