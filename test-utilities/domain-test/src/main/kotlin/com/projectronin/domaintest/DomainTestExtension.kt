package com.projectronin.domaintest

import io.github.classgraph.ClassGraph
import mu.KLogger
import mu.KotlinLogging
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource

/**
 * The Junit5 test extension that powers the domain tests.  Use with:
 * ```
 * @ExtendWith(DomainTestExtension::class)
 * ```
 *
 * Requires that your test suite has a single class that implements [DomainTestServicesProvider].  Classpath
 * scanning will be used to find it.  A single instance of this will be used for all tests in the suite.
 */
open class DomainTestExtension : BeforeAllCallback, CloseableResource {

    private val testContext = DomainTestSetupContext()
    private val logger: KLogger = KotlinLogging.logger { }

    override fun beforeAll(context: ExtensionContext) {
        if (!started) {
            started = true

            val ci = runCatching {
                ClassGraph().enableClassInfo().scan().getClassesImplementing(DomainTestServicesProvider::class.java)
                    .find { it.implementsInterface(DomainTestServicesProvider::class.java) && !it.isAbstract && !it.isInterface }
            }
                .onFailure { logger.error(it) { "Exception looking for implementation of DomainTestServicesProvider" } }
                .getOrElse { e -> throw RuntimeException("Could not start test context", e) } ?: throw RuntimeException("No implementation of DomainTestServicesProvider found")
            val provider = ci.loadClass().getConstructor().newInstance() as DomainTestServicesProvider
            provider.configurer()(testContext)
            testContext.start()
            context.root.getStore(ExtensionContext.Namespace.GLOBAL).put("domain-test-extension", this)
        }
    }

    override fun close() {
        testContext.stop()
    }

    companion object {
        @Volatile
        private var started = false
    }
}
