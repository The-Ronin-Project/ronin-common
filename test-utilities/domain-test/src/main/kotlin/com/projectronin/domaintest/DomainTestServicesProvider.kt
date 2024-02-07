package com.projectronin.domaintest

interface DomainTestServicesProvider {

    fun configurer(): DomainTestSetupContext.() -> Unit
}
