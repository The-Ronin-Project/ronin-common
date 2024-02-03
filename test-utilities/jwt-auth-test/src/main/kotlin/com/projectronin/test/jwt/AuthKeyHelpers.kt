package com.projectronin.test.jwt

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Generates a random RSA key for test use.  You want to make sure you use the same key for a mock auth server
 * and for the tokens you're verifying against it, so you probably want to generate one and use it for the course of the tests.
 *
 * If you are testing against something like prodeng-auth-service, you also need to make sure that you're using the RSA key
 * that the service itself is using.  Helpers in the domain-test module can be used to do that.
 */
fun generateRandomRsa(): RSAKey {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()
    return RSAKey.Builder(keyPair.public as RSAPublicKey)
        .privateKey(keyPair.private as RSAPrivateKey)
        .keyID(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)))
        .build()
}

/**
 * Creates a JWKSet for use by a  mock auth server.
 */
fun createJWKS(key: RSAKey): JWKSet = JWKSet(key)

/**
 * Used to translate a JWKSet to a JSON representation to be served from a mock auth server
 */
fun createJWKSForPublicDisplay(jwkSet: JWKSet): String = JwtAuthTestJackson.objectMapper.writeValueAsString(jwkSet.toJSONObject(true))
