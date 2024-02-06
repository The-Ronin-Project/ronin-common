package com.projectronin.tenant.config

import com.projectronin.kafka.config.ClusterProperties
import com.projectronin.tenant.handlers.TenantEventHandler

data class TenantStreamConfig(
    val clusterProperties: ClusterProperties,
    val applicationId: String,
    val tenantTopic: String = "oci.us-phoenix-1.ronin-tenant.tenant.v1",
    val dlqTopic: String,
    val handler: TenantEventHandler
)
