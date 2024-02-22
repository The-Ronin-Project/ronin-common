package com.projectronin.tenant.handlers

import com.projectronin.json.tenant.v1.TenantV1Schema
import com.projectronin.kafka.data.RoninEvent

interface TenantEventHandler {
    fun create(command: RoninEvent<TenantV1Schema>)
    fun update(command: RoninEvent<TenantV1Schema>)
    fun delete(command: RoninEvent<TenantV1Schema?>)
}
