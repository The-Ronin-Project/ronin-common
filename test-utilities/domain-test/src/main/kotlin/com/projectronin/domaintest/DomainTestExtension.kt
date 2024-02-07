package com.projectronin.domaintest

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource
import java.util.ServiceLoader

class DomainTestExtension : BeforeAllCallback, CloseableResource {

    private val testContext = DomainTestSetupContext()

    override fun beforeAll(context: ExtensionContext) {
        if (!started) {
            started = true
            val provider = ServiceLoader.load(DomainTestServicesProvider::class.java).findFirst().get()
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
