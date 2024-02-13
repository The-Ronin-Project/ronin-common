package com.projectronin.test.jwt

import com.fasterxml.jackson.module.kotlin.readValue
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
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
import java.util.Date

/**
 * Given a key and issuer, generate a JWT.  If desired, pass a customizer like so:
 *
 * ```
 * generateToken(key, issuer) {
 *     subject(UUID.randomUUID().toString())
 *         .expirationTime(expireDate)
 * }
 * ```
 */
fun generateToken(rsaKey: RSAKey, issuer: String, claimSetCustomizer: JWTClaimsSet.Builder.() -> JWTClaimsSet.Builder = { this }): String {
    val signedJWT = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build(),
        claimSetCustomizer(
            JWTClaimsSet.Builder()
                .subject("alice")
                .issueTime(Date())
                .issuer(issuer)
        )
            .build()
    )

    signedJWT.sign(RSASSASigner(rsaKey))
    return signedJWT.serialize()
}

/**
 * Constructs a default RoninClaims object with most values set as if for a provider token.  Override
 * any of the values as parameters.
 */
fun defaultRoninClaims(
    id: String = "9bc3abc9-d44d-4355-b81d-57e76218a954",
    userType: RoninUserType = RoninUserType.Provider,
    fullName: String = "Jennifer Przepiora",
    familyName: String? = "Przepiora",
    givenName: String? = "Jennifer",
    tenantId: String? = "apposnd",
    patientUdpId: String? = "apposnd-231982009",
    patientFhirId: String? = "231982009",
    providerUdpId: String? = "apposnd-eSC7e62xM4tbHbRbARd1o0kw3",
    providerFhirId: String? = "eSC7e62xM4tbHbRbARd1o0kw3",
    preferredTimeZone: String? = "America/Los_Angeles",
    identities: List<RoninUserIdentity> = listOf(
        RoninUserIdentity(
            type = RoninUserIdentityType.ProviderUdpId,
            tenantId = tenantId,
            id = providerUdpId
        )
    ),
    authenticationSchemes: List<RoninAuthenticationScheme> = listOf(
        RoninAuthenticationScheme(
            type = RoninAuthenticationSchemeType.SmartOnFhir,
            tenantId = tenantId,
            id = providerFhirId
        )
    )
): RoninClaims {
    return RoninClaims(
        user = RoninUser(
            id = id,
            userType = userType,
            name = RoninName(
                fullText = fullName,
                familyName = familyName,
                givenName = givenName?.let { listOf(it) } ?: emptyList(),
                prefix = emptyList(),
                suffix = emptyList()
            ),
            preferredTimeZone = preferredTimeZone,
            loginProfile = RoninLoginProfile(
                accessingTenantId = tenantId,
                accessingPatientUdpId = patientUdpId,
                accessingProviderUdpId = providerUdpId,
                accessingExternalPatientId = patientFhirId
            ),
            identities = identities,
            authenticationSchemes = authenticationSchemes
        )
    )
}

fun JWTClaimsSet.Builder.roninClaim(claims: RoninClaims) = apply {
    claim(
        RoninClaimsAuthentication.roninClaimsKey,
        JwtAuthTestJackson.objectMapper.readValue(
            JwtAuthTestJackson.objectMapper.writeValueAsString(
                claims
            )
        )
    )
}

/**
 * Returns a JWT auth token.  See `defaultRoninClaims()` for the defaults
 * that get set into it.  You can pass a block that customizes the code, e.g.:
 *
 * ```
 * val token = jwtAuthToken(key, issuer) {
 *     withUserType(RoninUserType.RoninEmployee)
 *         .withScopes("admin:read", "admin:write", "tenant:delete")
 * }
 * ```
 */
fun jwtAuthToken(rsaKey: RSAKey, issuer: String, block: RoninTokenBuilderContext.() -> Unit = {}): String {
    val ctx = RoninTokenBuilderContext(rsaKey, issuer, defaultRoninClaims().user!!)
    block(ctx)
    return ctx.buildToken()
}

/**
 * Internally used for the customization blocks in functions like `jwtAuthToken()`
 */
class RoninTokenBuilderContext(rsaKey: RSAKey, val issuer: String, roninUser: RoninUser) {

    private var id: String = roninUser.id
    private var userType: RoninUserType = roninUser.userType
    private var name: RoninName? = roninUser.name
    private var preferredTimeZone: String? = roninUser.preferredTimeZone
    private var loginProfile: RoninLoginProfile? = roninUser.loginProfile
    private var identities: MutableList<RoninUserIdentity> = roninUser.identities.toMutableList()
    private var authenticationSchemes: MutableList<RoninAuthenticationScheme> = roninUser.authenticationSchemes.toMutableList()
    private var _rsaKey: RSAKey = rsaKey

    private val scopes = mutableListOf<String>()
    private var builderCustomizers = mutableListOf<JWTClaimsSet.Builder.() -> JWTClaimsSet.Builder>(
        {
            subject("alice")
                .run { scopes.takeIf { it.isNotEmpty() }?.let { claim("scope", it.toList()) } ?: this }
        }
    )

    /**
     * Builds the token and serializes it.
     */
    fun buildToken(): String {
        val roninClaims = RoninClaims(
            RoninUser(
                id = id,
                userType = userType,
                name = name,
                preferredTimeZone = preferredTimeZone,
                loginProfile = loginProfile,
                identities = identities,
                authenticationSchemes = authenticationSchemes
            )
        )
        return generateToken(
            rsaKey = _rsaKey,
            issuer = issuer
        ) {
            builderCustomizers.fold(roninClaim(roninClaims)) { builder, block ->
                block(builder)
            }
        }
    }

    /**
     * Changes the RSA key that's being used.
     */
    fun withRsaKey(rsaKey: RSAKey): RoninTokenBuilderContext {
        this._rsaKey = rsaKey
        return this
    }

    /**
     * Sets the list of scopes.
     */
    fun withoutScopes(): RoninTokenBuilderContext {
        scopes.clear()
        return this
    }

    /**
     * Adds new scopes to the list of existing scopes.  Creates a new list if necessary.
     */
    fun withScopes(vararg scope: String): RoninTokenBuilderContext {
        scopes += scope
        return this
    }

    /**
     * Changes the `iss` issuer field of the token
     */
    fun withIssuer(issuer: String): RoninTokenBuilderContext = withTokenCustomizer { issuer(issuer) }

    /**
     * Sets the `sub` field of the token
     */
    fun withSubject(subject: String): RoninTokenBuilderContext = withTokenCustomizer { subject(subject) }

    /**
     * Sets the `iat` field of the token to the given date.
     */
    fun withIat(issuedAt: Date): RoninTokenBuilderContext = withIssueTime(issuedAt)

    fun withIssueTime(issuedAt: Date): RoninTokenBuilderContext = withTokenCustomizer { issueTime(issuedAt) }

    /**
     * Sets the `exp` field of the token to the given date.
     */
    fun withExpirationTime(expirationTime: Date): RoninTokenBuilderContext = withTokenCustomizer { expirationTime(expirationTime) }

    /**
     * Sets the `aud` field of the token to the given date
     */
    fun withAudience(vararg aud: String): RoninTokenBuilderContext = withTokenCustomizer { audience(aud.toList()) }

    fun withNotBeforeTime(nbf: Date): RoninTokenBuilderContext = withTokenCustomizer { notBeforeTime(nbf) }

    fun withJwtID(jwtId: String): RoninTokenBuilderContext = withTokenCustomizer { jwtID(jwtId) }

    /**
     * Sets the user id value of the ronin claims
     */
    fun withUserId(id: String): RoninTokenBuilderContext {
        this.id = id
        return this
    }

    /**
     * Sets the user type value of the ronin claims
     */
    fun withUserType(userType: RoninUserType): RoninTokenBuilderContext {
        this.userType = userType
        return this
    }

    /**
     * Sets the name field of the ronin claims
     */
    fun withName(name: RoninName?): RoninTokenBuilderContext {
        this.name = name
        return this
    }

    /**
     * Sets the preferred time zone of the ronin claims
     */
    fun withPreferredTimeZone(preferredTimeZone: String?): RoninTokenBuilderContext {
        this.preferredTimeZone = preferredTimeZone
        return this
    }

    /**
     * Sets the login profile of the ronin claims.  This is where you would set the tenant, patient, and provider ids.  The
     * easiest way to use it is like this:
     * ```
     * withLoginProfile() {
     *    withAccessingTenantId("apposnd")
     *    withAccessingPatientUdpId("apposnd-sS0b4s4hBhoJiDK6SAehxAlRHAkQMH")
     * }
     * ```
     */
    fun withLoginProfile(loginProfile: RoninLoginProfile? = null, block: RoninLoginProfileContext.() -> Unit = {}): RoninTokenBuilderContext {
        val ctx = RoninLoginProfileContext(loginProfile)
        block(ctx)
        this.loginProfile = ctx.build()
        return this
    }

    fun withUpdatedLoginProfile(block: RoninLoginProfileContext.() -> Unit = {}): RoninTokenBuilderContext = withLoginProfile(loginProfile, block)

    /**
     * Clears the list of identities
     */
    fun withoutIdentities(): RoninTokenBuilderContext {
        this.identities.clear()
        return this
    }

    /**
     * Adds identities to the user identity list.
     */
    fun withIdentities(vararg identities: RoninUserIdentity): RoninTokenBuilderContext {
        this.identities += identities
        return this
    }

    /**
     * Clears the authentication schemes list
     */
    fun withoutAuthenticationSchemes(): RoninTokenBuilderContext {
        this.authenticationSchemes.clear()
        return this
    }

    /**
     * Adds new authentication schemes
     */
    fun withAuthenticationSchemes(vararg authenticationSchemes: RoninAuthenticationScheme): RoninTokenBuilderContext {
        this.authenticationSchemes += authenticationSchemes
        return this
    }

    /**
     * Adds an arbitrary claim field to the token.
     */
    fun withClaim(key: String, value: Any?): RoninTokenBuilderContext = withTokenCustomizer { claim(key, value) }

    /**
     * Sets a function that will be used to directly manipulate the claimset builder after everything else has been
     * added but before the final token is built.
     */
    fun withTokenCustomizer(fn: JWTClaimsSet.Builder.() -> JWTClaimsSet.Builder): RoninTokenBuilderContext {
        builderCustomizers += fn
        return this
    }
}

/**
 * Used internally by `withLoginProfile {}`.  Methods should be self-explanatory, but note that if
 * all the fields are null, the result of building the profile will be a null profile.
 */
class RoninLoginProfileContext(loginProfile: RoninLoginProfile?) {

    private var accessingTenantId: String? = loginProfile?.accessingTenantId
    private var accessingProviderUdpId: String? = loginProfile?.accessingProviderUdpId
    private var accessingPatientUdpId: String? = loginProfile?.accessingPatientUdpId
    private var accessingExternalPatientId: String? = loginProfile?.accessingExternalPatientId

    fun withAccessingTenantId(accessingTenantId: String?): RoninLoginProfileContext {
        this.accessingTenantId = accessingTenantId
        return this
    }

    fun withAccessingProviderUdpId(accessingProviderUdpId: String?): RoninLoginProfileContext {
        this.accessingProviderUdpId = accessingProviderUdpId
        return this
    }

    fun withAccessingPatientUdpId(accessingPatientUdpId: String?): RoninLoginProfileContext {
        this.accessingPatientUdpId = accessingPatientUdpId
        return this
    }

    fun withAccessingExternalPatientId(accessingExternalPatientId: String?): RoninLoginProfileContext {
        this.accessingExternalPatientId = accessingExternalPatientId
        return this
    }

    internal fun build(): RoninLoginProfile? {
        return if (accessingTenantId != null || accessingProviderUdpId != null || accessingPatientUdpId != null || accessingExternalPatientId != null) {
            RoninLoginProfile(
                accessingTenantId = accessingTenantId,
                accessingProviderUdpId = accessingProviderUdpId,
                accessingPatientUdpId = accessingPatientUdpId,
                accessingExternalPatientId = accessingExternalPatientId
            )
        } else {
            null
        }
    }
}
