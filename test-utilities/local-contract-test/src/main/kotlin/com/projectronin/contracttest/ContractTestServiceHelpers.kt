package com.projectronin.contracttest

import com.projectronin.domaintest.DomainTestContext
import com.projectronin.domaintest.DomainTestSetupContext
import com.projectronin.domaintest.ProductEngineeringServiceContext
import java.io.File

fun contractTest(block: DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

suspend fun coContractTest(block: suspend DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

val DomainTestContext.localContractTestService: String
    get() = "service"

fun DomainTestSetupContext.withServiceUnderTest(file: File, fn: ProductEngineeringServiceContext.() -> Unit) {
    withProductEngineeringService("service", "ronin/base/java-springboot", "1.1.0") {
        fn(this)
        withCoverage()
        extraConfiguration {
            @Suppress("DEPRECATION")
            withFileSystemBind(file.absolutePath, "/app/app.jar")
        }
    }
}
