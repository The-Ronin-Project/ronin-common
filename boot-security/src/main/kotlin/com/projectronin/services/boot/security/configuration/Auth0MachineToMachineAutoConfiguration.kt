package com.projectronin.services.boot.security.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.web.servlet.invoke
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@ConfigurationProperties(prefix = Auth0MachineToMachineProperties.prefix)
class Auth0MachineToMachineProperties {
    companion object {
        const val prefix = "ronin.server.auth.m2m"
    }

    var enabled = false
    var issuer: String? = null
    var audience: String? = null
    var jwkUri: String? = null
}

@Configuration
@EnableWebSecurity
class Auth0MachineToMachineConfiguration(private val properties: Auth0MachineToMachineProperties) :
    WebSecurityConfigurerAdapter(false) {
    @Bean
    fun jwtValidator(): OAuth2TokenValidator<Jwt> {
        val validators = ArrayList(listOf(JwtValidators.createDefaultWithIssuer(properties.issuer)))

        if (properties.audience?.isNotBlank() == true) {
            validators.add(
                JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD) { aud -> properties.audience in aud }
            )
        }

        return DelegatingOAuth2TokenValidator(validators)
    }

    @Bean
    fun jwtDecoder() = JwtDecoders.fromOidcIssuerLocation<NimbusJwtDecoder>(properties.issuer)
        .apply { setJwtValidator(jwtValidator()) }

    override fun configure(http: HttpSecurity) {
        http {
            authorizeRequests {
                // TODO: this will eventually probably need to be configurable. At minimum we'll need to allow the health
                // check unauthorized once it exists.
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt {
                    jwkSetUri = properties.jwkUri ?: properties.issuer
                    jwtDecoder = jwtDecoder()
                }
            }
        }
    }
}

@Configuration
@EnableConfigurationProperties(Auth0MachineToMachineProperties::class)
@ConditionalOnProperty(prefix = Auth0MachineToMachineProperties.prefix, name = ["enabled"])
@Import(Auth0MachineToMachineConfiguration::class)
class Auth0MachineToMachineAutoConfiguration
