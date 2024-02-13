package com.projectronin.test.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthKeyHelpersTest {

    @Test
    fun `should generate key`() {
        val rsaKey = generateRandomRsa()
        assertThat(rsaKey).isNotNull
        assertThat(rsaKey.keyID).isNotNull
        assertThat(rsaKey.size()).isEqualTo(2048)
    }

    @Test
    fun `should construct jwks`() {
        val key = generateRandomRsa()
        val jwks = createJWKS(key)
        assertThat(jwks.keys).hasSize(1)
        assertThat(jwks.keys[0].keyID).isEqualTo(key.keyID)
    }

    @Test
    fun `should redact private keys from public version`() {
        val key = generateRandomRsa()
        val jwks = createJWKS(key)
        val body = JwtAuthTestJackson.objectMapper.readTree(createJWKSForPublicDisplay(jwks))
        assertThat(body.get("keys")?.isArray).isTrue()
        val keyMap = body.get("keys").get(0)
        assertThat(keyMap.get("kty").asText()).isNotBlank()
        assertThat(keyMap.get("e").asText()).isNotBlank()
        assertThat(keyMap.get("kid").asText()).isNotBlank()
        assertThat(keyMap.get("n").asText()).isNotBlank()
        assertThat(keyMap).hasSize(4)
    }
}
