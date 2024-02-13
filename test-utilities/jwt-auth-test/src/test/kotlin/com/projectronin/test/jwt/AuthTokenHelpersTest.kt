package com.projectronin.test.jwt

import com.fasterxml.jackson.module.kotlin.readValue
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import com.projectronin.auth.RoninClaimsAuthentication
import com.projectronin.auth.token.RoninAuthenticationScheme
import com.projectronin.auth.token.RoninAuthenticationSchemeType
import com.projectronin.auth.token.RoninClaims
import com.projectronin.auth.token.RoninLoginProfile
import com.projectronin.auth.token.RoninName
import com.projectronin.auth.token.RoninUser
import com.projectronin.auth.token.RoninUserIdentity
import com.projectronin.auth.token.RoninUserIdentityType
import com.projectronin.auth.token.RoninUserType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class AuthTokenHelpersTest {

    private val key = generateRandomRsa()
    private val issuer = "https://example.org"

    @Test
    fun `generate token works`() {
        val token = generateToken(key, issuer)

        val signedJWT = verifyJwt(token)
        assertThat(signedJWT.jwtClaimsSet.subject).isEqualTo("alice")
        assertThat(signedJWT.jwtClaimsSet.issueTime).isNotNull()
        assertThat(signedJWT.jwtClaimsSet.issuer).isEqualTo(issuer)
    }

    @Test
    fun `jwtAuthToken works in a simple context`() {
        val decoded = verifyJwt(jwtAuthToken(key, issuer))
        assertThat(decoded.jwtClaimsSet.subject).isEqualTo("alice")
        assertThat(decoded.jwtClaimsSet.issueTime).isNotNull()
        assertThat(decoded.jwtClaimsSet.issuer).isEqualTo(issuer)
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims()
        )
    }

    @Test
    fun `jwtAuthToken works with a different key`() {
        val otherKey = generateRandomRsa()
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withRsaKey(otherKey)
            },
            otherKey
        )
        assertThat(decoded.jwtClaimsSet.subject).isEqualTo("alice")
        assertThat(decoded.jwtClaimsSet.issueTime).isNotNull()
        assertThat(decoded.jwtClaimsSet.issuer).isEqualTo(issuer)
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims()
        )
    }

    @Test
    fun `jwtAuthToken incorporates scopes`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withScopes("foo", "bar")
                withScopes("baz")
            }
        )
        assertThat(decoded.jwtClaimsSet.getStringListClaim("scope")).isEqualTo(listOf("foo", "bar", "baz"))
    }

    @Test
    fun `jwtAuthToken incorporates added scopes`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withScopes("foo", "bar").withoutScopes().withScopes("baz")
            }
        )
        assertThat(decoded.jwtClaimsSet.getStringListClaim("scope")).isEqualTo(listOf("baz"))
    }

    @Test
    fun `jwtAuthToken incorporates basic supported claims`() {
        val nbf = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        val iat = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        val exp = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(30))
        val id = UUID.randomUUID().toString()
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withIssuer("https://example.edu")
                withSubject("kim")
                // proves last one wins
                withIat(Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(72)))
                withIssueTime(Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(30)))
                withIssueTime(iat)
                withExpirationTime(exp)
                withAudience("https://some.service.io")
                withNotBeforeTime(nbf)
                withJwtID(id)
            }
        )
        assertThat(decoded.jwtClaimsSet.issuer).isEqualTo("https://example.edu")
        assertThat(decoded.jwtClaimsSet.subject).isEqualTo("kim")
        assertThat(decoded.jwtClaimsSet.issueTime).isEqualTo(iat)
        assertThat(decoded.jwtClaimsSet.expirationTime).isEqualTo(exp)
        assertThat(decoded.jwtClaimsSet.audience).containsExactly("https://some.service.io")
        assertThat(decoded.jwtClaimsSet.notBeforeTime).isEqualTo(nbf)
        assertThat(decoded.jwtClaimsSet.jwtid).isEqualTo(id)
    }

    @Test
    fun `jwtAuthToken should set user id`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withUserId("3dadd8e0-f934-48f0-b88f-2e6a80157e39")
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                userId = "3dadd8e0-f934-48f0-b88f-2e6a80157e39"
            )
        )
    }

    @Test
    fun `jwtAuthToken should set user type`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withUserType(RoninUserType.RoninEmployee)
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                userType = RoninUserType.RoninEmployee
            )
        )
    }

    @Test
    fun `jwtAuthToken should set name`() {
        val name = RoninName(
            "Francisco Viens",
            "Viens",
            listOf("Francisco"),
            listOf("Dr"),
            listOf("PhD")
        )
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withName(name)
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                roninName = name
            )
        )
    }

    @Test
    fun `jwtAuthToken should set time zone`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withPreferredTimeZone("America/Chicago")
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                timeZone = "America/Chicago"
            )
        )
    }

    @Test
    fun `jwtAuthToken with no login profile`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withLoginProfile(null)
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                loginProfile = null
            )
        )
    }

    @Test
    fun `jwtAuthToken with login profile specifying starting point`() {
        val lp = RoninLoginProfile(
            accessingTenantId = "apposnd",
            accessingProviderUdpId = null,
            accessingPatientUdpId = null,
            accessingExternalPatientId = null
        )
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withLoginProfile(lp) {
                    withAccessingExternalPatientId("xlLNx3Ydli5jBi5oPXeDP74UDpDEym")
                }
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                loginProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = "xlLNx3Ydli5jBi5oPXeDP74UDpDEym"
                )
            )
        )
    }

    @Test
    fun `jwtAuthToken with login profile not specifying starting point`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withLoginProfile {
                    withAccessingTenantId("xlLNx3")
                }
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                loginProfile = RoninLoginProfile(
                    accessingTenantId = "xlLNx3",
                    accessingProviderUdpId = null,
                    accessingPatientUdpId = null,
                    accessingExternalPatientId = null
                )
            )
        )
    }

    @Test
    fun `jwtAuthToken with updated login profile`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withUpdatedLoginProfile {
                    withAccessingPatientUdpId("apposnd-WIhw9AVX10eb7WMyoT3qqDSth3S3w5")
                    withAccessingProviderUdpId("apposnd-L8XDSBJNDPNVInaa4yhAskdjU9MaIs")
                }
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                loginProfile = RoninLoginProfile(
                    accessingTenantId = "apposnd",
                    accessingPatientUdpId = "apposnd-WIhw9AVX10eb7WMyoT3qqDSth3S3w5",
                    accessingProviderUdpId = "apposnd-L8XDSBJNDPNVInaa4yhAskdjU9MaIs",
                    accessingExternalPatientId = "231982009"
                )
            )
        )
    }

    @Test
    fun `jwtAuthToken with updated to null`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withUpdatedLoginProfile {
                    withAccessingTenantId(null)
                    withAccessingPatientUdpId(null)
                    withAccessingProviderUdpId(null)
                    withAccessingExternalPatientId(null)
                }
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                loginProfile = null
            )
        )
    }

    @Test
    fun `jwtAuthToken with identities`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withoutIdentities().withIdentities(
                    RoninUserIdentity(
                        type = RoninUserIdentityType.GoogleAccountId,
                        tenantId = "apposnd",
                        id = "foo"
                    )
                )
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                identities = listOf(
                    RoninUserIdentity(
                        type = RoninUserIdentityType.GoogleAccountId,
                        tenantId = "apposnd",
                        id = "foo"
                    )
                )
            )
        )
    }

    @Test
    fun `jwtAuthToken with schemes`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withoutAuthenticationSchemes().withAuthenticationSchemes(
                    RoninAuthenticationScheme(
                        type = RoninAuthenticationSchemeType.Auth0M2M,
                        tenantId = "apposnd",
                        id = "foo"
                    )
                )
            }
        )
        assertThat(decoded.decodeRoninClaim).usingRecursiveComparison().isEqualTo(
            expectedClaims(
                schemes = listOf(
                    RoninAuthenticationScheme(
                        type = RoninAuthenticationSchemeType.Auth0M2M,
                        tenantId = "apposnd",
                        id = "foo"
                    )
                )
            )
        )
    }

    @Test
    fun `jwtAuthToken with arbitrary claim`() {
        val decoded = verifyJwt(
            jwtAuthToken(key, issuer) {
                withClaim("https://projectonin.io/tenantId", "LHHe5X")
            }
        )
        assertThat(decoded.jwtClaimsSet.getClaim("https://projectonin.io/tenantId")).isEqualTo("LHHe5X")
    }

    private fun verifyJwt(token: String, rsaKey: RSAKey = key): SignedJWT {
        val signedJWT = SignedJWT.parse(token)
        val verifier: JWSVerifier = RSASSAVerifier(rsaKey.toRSAPublicKey())
        assertThat(signedJWT.verify(verifier)).isTrue()
        return signedJWT
    }

    private val SignedJWT.decodeRoninClaim: RoninClaims
        get() = JwtAuthTestJackson.objectMapper.readValue(JwtAuthTestJackson.objectMapper.writeValueAsString(jwtClaimsSet.getClaim(RoninClaimsAuthentication.roninClaimsKey)))

    private fun expectedClaims(
        userId: String = "9bc3abc9-d44d-4355-b81d-57e76218a954",
        userType: RoninUserType = RoninUserType.Provider,
        roninName: RoninName? = RoninName(
            fullText = "Jennifer Przepiora",
            familyName = "Przepiora",
            givenName = listOf("Jennifer"),
            prefix = emptyList(),
            suffix = emptyList()
        ),
        timeZone: String? = "America/Los_Angeles",
        loginProfile: RoninLoginProfile? = RoninLoginProfile(
            accessingTenantId = "apposnd",
            accessingPatientUdpId = "apposnd-231982009",
            accessingProviderUdpId = "apposnd-eSC7e62xM4tbHbRbARd1o0kw3",
            accessingExternalPatientId = "231982009"
        ),
        identities: List<RoninUserIdentity> = listOf(
            RoninUserIdentity(
                type = RoninUserIdentityType.ProviderUdpId,
                tenantId = "apposnd",
                id = "apposnd-eSC7e62xM4tbHbRbARd1o0kw3"
            )
        ),
        schemes: List<RoninAuthenticationScheme> = listOf(
            RoninAuthenticationScheme(
                type = RoninAuthenticationSchemeType.SmartOnFhir,
                tenantId = "apposnd",
                id = "eSC7e62xM4tbHbRbARd1o0kw3"
            )
        )
    ) = RoninClaims(
        user = RoninUser(
            id = userId,
            userType = userType,
            name = roninName,
            preferredTimeZone = timeZone,
            loginProfile = loginProfile,
            identities = identities,
            authenticationSchemes = schemes
        )
    )
}
