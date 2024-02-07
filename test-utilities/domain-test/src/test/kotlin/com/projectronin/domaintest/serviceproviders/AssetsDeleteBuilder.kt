package com.projectronin.domaintest.serviceproviders

import com.projectronin.common.TenantId
import com.projectronin.domaintest.DatabaseTables
import com.projectronin.domaintest.DeleteBuilder
import com.projectronin.domaintest.Table
import java.util.UUID

object AssetsDatabaseTables : DatabaseTables {
    val PATIENT_ASSETS_TABLE = Table.table<UUID, UUID>("patient_asset", "id")
        .convertId { "'$it'" }
        .convertEntity { "'$it'" }
        .build()
    val PATIENT_ALIAS_TABLE = Table.table<Pair<TenantId, String>, UUID>("patient_alias", "(tenant_id, patient_id)")
        .convertId { "('${it.first}', '${it.second}')" }
        .convertEntity { TODO("Models not available") }
        .build()
    val TENANT_TABLE = Table.table<TenantId, TenantId>("tenant", "tenant_id")
        .convertId { "'$it'" }
        .convertEntity { TODO("Models not available") }
        .build()
    override val dbName: String = "assets"
    override val tablesInDeleteOrder: List<Table<*, *>> = listOf(PATIENT_ASSETS_TABLE, PATIENT_ALIAS_TABLE, TENANT_TABLE)
}

fun assetsDeleteBuilder(): DeleteBuilder = DeleteBuilder(AssetsDatabaseTables)
