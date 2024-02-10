package com.projectronin.contracttest.provider

import com.projectronin.contracttest.ContractTestServicesProvider
import com.projectronin.contracttest.blueprinthack.BlueprintJarExtractor
import com.projectronin.contracttest.withServiceUnderTest
import com.projectronin.domaintest.DomainTestSetupContext
import com.projectronin.domaintest.internalJdbcUrlFor
import com.projectronin.domaintest.internalOidcIssuer
import java.nio.file.Files

class ContractTestProvider : ContractTestServicesProvider {

    override fun configurer(): DomainTestSetupContext.() -> Unit {
        return {
            val tempFile = Files.createTempDirectory("blueprint-contract-test").toFile()
            tempFile.deleteOnExit()
            val libFile = BlueprintJarExtractor.writeBlueprintJarTo(tempFile)

            withMySQL()
            withWireMock {
                withOIDCSupport()
            }
            withServiceUnderTest(libFile) {
                dependsOnMySQL("blueprint")
                dependsOnWireMock()
                configYaml(
                    """
                    spring:
                      datasource:
                        url: ${internalJdbcUrlFor("blueprint")}
                      liquibase:
                        enabled: true
                    ronin:
                      auth:
                        issuers:
                          - ${internalOidcIssuer()}
                    """.trimIndent()
                )
            }
        }
    }
}
