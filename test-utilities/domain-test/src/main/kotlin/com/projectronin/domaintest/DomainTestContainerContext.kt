package com.projectronin.domaintest

import org.testcontainers.containers.GenericContainer

interface DomainTestContainerContext {

    val dependencies: Set<String>
        get() = emptySet()

    fun createContainer(): GenericContainer<*>

    fun bootstrap(container: GenericContainer<*>)
}
