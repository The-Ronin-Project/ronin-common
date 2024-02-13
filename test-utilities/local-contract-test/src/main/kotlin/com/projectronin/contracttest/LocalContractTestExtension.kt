package com.projectronin.contracttest

import com.projectronin.domaintest.DomainTestExtension

/**
 * Renames DomainTestExtension so you can use it like:
 * ```
 * @ExtendWith(LocalContractTestExtension::class)
 * ```
 */
class LocalContractTestExtension : DomainTestExtension()
