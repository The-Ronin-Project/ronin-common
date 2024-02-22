package com.projectronin.tenant.handlers

import com.projectronin.common.TenantId
import com.projectronin.json.tenant.v1.TenantV1Schema

interface TenantEventHandler {
    fun create(data: TenantV1Schema)
    fun update(data: TenantV1Schema)
    fun delete(tenantId: TenantId)
}
