package com.projectronin.domaintest

import org.testcontainers.containers.GenericContainer

/**
 * An interface that defines a docket container to be executed by
 * testcontainers as part of a domain test.  Each service that will
 * run during the domain test needs to implement this interface.
 * Examples are [KafkaServiceContext], [ProductEngineeringServiceContext],
 * etc.
 */
interface DomainTestContainerContext {

    /**
     * A set of service names that this context depends on.  For
     * example, if `auth` needs `mysql` to start, `mysql` should be
     * in `auth`'s dependency list.  Only add services here
     * that must be present at startup.
     */
    val dependencies: Set<DomainTestContainer>
        get() = emptySet()

    /**
     * Creates the [GenericContainer] that will be started
     * by testcontainers.
     */
    fun createContainer(): GenericContainer<*>

    /**
     * After the container is started, this gets called with
     * the running container instance.  Can be used to set up anything
     * like DBs or similar.
     */
    fun bootstrap(container: GenericContainer<*>)


    /**
     * Before a container is shut down, do something.  Maybe you need
     * to extract a file.
     */
    fun teardown(container: GenericContainer<*>) {
        // by default does nothing
    }
}
