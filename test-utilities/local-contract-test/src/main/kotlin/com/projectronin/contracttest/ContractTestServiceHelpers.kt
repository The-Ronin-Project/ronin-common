package com.projectronin.contracttest

import com.projectronin.domaintest.DomainTestContext
import com.projectronin.domaintest.DomainTestSetupContext
import com.projectronin.domaintest.ProductEngineeringServiceContext
import java.io.File

/**
 * No different from [com.projectronin.domaintest.domainTest], but maintains contract test naming.
 */
fun contractTest(block: DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

/**
 * No different from [com.projectronin.domaintest.coDomainTest], but maintains contract test naming.
 */
suspend fun coContractTest(block: suspend DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

/**
 * Service name of the 'local contract test service under test', that is the service you're building now.
 */
val localContractTestService: String
    get() = "service"

/**
 * Instantiates a service under test, where you provide a JAR file location and it is executed as if it was a regular PE service.
 *
 * ```
 * withServiceUnderTest(standardSpringBootJarFile) {
 *     dependsOnMySQL("blueprint")
 *     dependsOnWireMock()
 *     configYaml(
 *         """
 *         spring:
 *           datasource:
 *             url: ${internalJdbcUrlFor("blueprint")}
 *           liquibase:
 *             enabled: true
 *         ronin:
 *           auth:
 *             issuers:
 *               - ${internalOidcIssuer()}
 *         """.trimIndent()
 *     )
 * }
 * ```
 */
fun DomainTestSetupContext.withServiceUnderTest(file: File, fn: ProductEngineeringServiceContext.() -> Unit) {
    withProductEngineeringService(ServiceUnderTest, "1.1.0") {
        fn(this)
        withCoverage()
        extraConfiguration {
            @Suppress("DEPRECATION")
            withFileSystemBind(file.absolutePath, "/app/app.jar")
        }
    }
}

/**
 * Tries to find the standard spring boot lib.  Depends on a system property named `ronin.contracttest.libdir` being set by the standard local contract test project.
 */
val standardSpringBootJarFile: File
    get() = (File(System.getProperty("ronin.contracttest.libdir")).listFiles() ?: arrayOf<File>())
        .firstOrNull { f -> f.extension == "jar" && !f.name.contains("javadoc|sources|plain".toRegex()) }
        ?: throw IllegalStateException("Couldn't find jar file under ${System.getProperty("ronin.contracttest.libdir")}")
