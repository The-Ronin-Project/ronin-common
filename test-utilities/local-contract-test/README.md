# Local Contract Test Framework

This module creates a local contract test framework based on [domain-test](../domain-test). It provides some aliases of the domain test classes that are named more related to contract tests,
and can be extended to create contract-test-specific functionality in the future.

The most relevant part of _this_ module for contract tests is [DomainTestSetupContext.withServiceUnderTest](src/main/kotlin/com/projectronin/contracttest/ContractTestServiceHelpers.kt). It
needs to be provided with the location of the boot jar produced by the project. Assuming you are using the standard local contract test plugin, you can do something like this:

```kotlin
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
            withServiceUnderTest(standardSpringBootJarFile) {
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
```

Then you can write tests like:

```kotlin
@ExtendWith(LocalContractTestExtension::class)
class LocalContractTestSetupTest {

    @Test
    fun `should do something`() = contractTest {
        // test stuff.
    }
}
```
