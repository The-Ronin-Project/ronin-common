package com.projectronin.domaintest

import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.nimbusds.jose.jwk.RSAKey

/**
 * Globally configures the default auth provider (wiremock/OIDC or auth service).  If the defaults here are used, it prefers auth service.
 *
 * However, you may change the default by providing your own
 */
internal object AuthContext {
    var defaultAuthProvider: AuthData = DefaultAuthData
}

/**
 * An interface for providing authentication data.  Supply this if you want to globally override the auth data.
 */
interface AuthData {
    fun issuer(): DomainTestContext.() -> String

    fun rsaKey(): DomainTestContext.() -> RSAKey
}

/**
 * The default auth data
 */
object DefaultAuthData : AuthData {
    /**
     * An RSAKey for token signing that is associated with a mock oauth2 server created by  [WireMockServiceContext.withOIDCSupport].  Used as a fallback for tokens
     * if you didn't create an auth server via [DomainTestSetupContext.withAuth]
     */
    private val mockRsaKey: RSAKey by lazy { WireMockServiceContext.instance.rsaKey }

    /**
     * An RSAKey extracted from a running auth service created by [DomainTestSetupContext.withAuth].  Used by default if the auth server is running.
     */
    private var _authServiceRsaKey: RSAKey? = null

    override fun issuer(): DomainTestContext.() -> String {
        return {
            if (ProductEngineeringServiceContext.serviceMap[KnownServices.Auth.serviceName] != null) {
                authServiceIssuer()
            } else {
                oidcIssuer()
            }
        }
    }

    override fun rsaKey(): DomainTestContext.() -> RSAKey {
        return {
            if (ProductEngineeringServiceContext.serviceMap[KnownServices.Auth.serviceName] != null) {
                if (_authServiceRsaKey == null) {
                    request {
                        get("/oauth2/jwks", KnownServices.Auth)
                    }.execute { }

                    val clientConfig = ClientConfig()
                    clientConfig.clusterName = "spring-session-cluster"
                    clientConfig.setProperty("hazelcast.logging.type", "slf4j")
                    clientConfig.networkConfig.addAddress("localhost:${exposedServicePort(KnownServices.Auth, 5701)}")

                    val client = HazelcastClient.newHazelcastClient(clientConfig)

                    _authServiceRsaKey = client.getReplicatedMap<String, RSAKey>("rsaKey")["key"]!!

                    client.shutdown()
                }
                _authServiceRsaKey!!
            } else {
                mockRsaKey
            }
        }
    }
}
