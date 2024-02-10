package com.projectronin.domaintest

/**
 * Implement this in your test suite to define what services need to be run.
 */
interface DomainTestServicesProvider {

    /**
     * Return a lambda that will be called to set up the tests.  For example
     * ```
     * class DocumentsServicesProvider : DomainTestServicesProvider {
     *    override fun configurer(): DomainTestSetupContext.() -> Unit = {
     *        withMySQL()
     *        withKafka()
     *        withWireMock {
     *            withM2MSupport()
     *        }
     *        withAuth("1.0.39")
     *        withGateway("1.0.25")
     *        withProductEngineeringService(KnownServices.DocumentApi, "2.0.16") {
     *            withDebugging(false)
     *            withCoverage()
     *            dependsOnMySQLDatabase("document_api")
     *            dependsOnKafka(
     *                documentEventsTopic,
     *                documentsDlqTopic,
     *                tenantTopic
     *            )
     *            dependsOnWireMock()
     *        }
     *    }
     * }
     * ```
     * See DocumentsServicesProvider in the tests of this module for an extended example.]
     */
    fun configurer(): DomainTestSetupContext.() -> Unit
}
