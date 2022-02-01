package com.projectronin.services.boot.security.configuration

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata

private val FILTER_CLASSES = setOf(
    "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
    "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
)

/**
 * <p>Disables the Spring Security autoconfiguration provided through spring-boot-starter-security.</p>
 *
 * <p>By pulling in spring-boot-starter-security, we get Spring Security's autoconfiguration. The problem with that is
 * that Spring Security's autoconfiguration is really bad (it enabled a login page backed by an in memory
 * username/password store). This doesn't matter in cases where we define our own security config (i.e. if the consumer
 * has one of the Ronin Services security adapters enabled), but we use this to disable Spring Security's default config
 * in the case that none of the Ronin Services security features are enabled.</p>
 */
class SpringSecurityAutoConfigurationImportFilter : AutoConfigurationImportFilter {
    override fun match(
        autoConfigurationClasses: Array<out String>,
        autoConfigurationMetadata: AutoConfigurationMetadata
    ): BooleanArray {
        return autoConfigurationClasses.map { it !in FILTER_CLASSES }.toBooleanArray()
    }
}
