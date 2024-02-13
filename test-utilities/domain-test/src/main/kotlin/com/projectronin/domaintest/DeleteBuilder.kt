package com.projectronin.domaintest

/**
 * A utility class for building DB cleanups in your domain or contract tests.  Typically, you will be creating a DatabaseTables
 * instance for your specific DB and then a helper function to utilize it, like this:
 *
 * ```
 * object AssetsDatabaseTables : DatabaseTables {
 *     val PATIENT_ASSETS_TABLE = Table.table<UUID, UUID>("patient_asset", "id")
 *         .convertId { "'$it'" }
 *         .convertEntity { "'$it'" }
 *         .build()
 *     val PATIENT_ALIAS_TABLE = Table.table<Pair<TenantId, String>, UUID>("patient_alias", "(tenant_id, patient_id)")
 *         .convertId { "('${it.first}', '${it.second}')" }
 *         .convertEntity { throw IllegalStateException("Models not available") }
 *         .build()
 *     val TENANT_TABLE = Table.table<TenantId, TenantId>("tenant", "tenant_id")
 *         .convertId { "'$it'" }
 *         .convertEntity { throw IllegalStateException("Models not available") }
 *         .build()
 *     override val dbName: String = "assets"
 *     override val tablesInDeleteOrder: List<Table<*, *>> = listOf(PATIENT_ASSETS_TABLE, PATIENT_ALIAS_TABLE, TENANT_TABLE)
 * }
 * fun assetsDeleteBuilder(): DeleteBuilder = DeleteBuilder(AssetsDatabaseTables)
 * ```
 *
 * and then in a test like:
 *
 * ```
 * @Test
 * fun `should write, read, and cleanup db values`() = domainTest {
 *     val tenantId = TenantId.random()
 *     withDatabase("assets") {
 *         cleanupWithDeleteBuilder(assetsDeleteBuilder()) { cleanup ->
 *
 *             cleanup += AssetsDatabaseTables.TENANT_TABLE.recordForId(tenantId)
 *
 *             // do something that would create the record that you will be deleting
 *             // do your assertions, etc
 *         }
 *     }
 * }
 * ```
 */
class DeleteBuilder(
    /**
     * a list of pairs of table name to id field name
     */
    private val tables: DatabaseTables
) {
    private val records = mutableListOf<TableRecord<*, *>>()

    /**
     * allows you to do += record
     */
    operator fun plusAssign(record: TableRecord<*, *>) {
        records.add(record)
    }

    /**
     * builds the sql statements needed to clean up the database
     */
    fun build() = tables.tablesInDeleteOrder.mapNotNull { buildDeleteSqlOrNull(it) }

    private fun buildDeleteSqlOrNull(
        table: Table<*, *>
    ): String? =
        records.filter { it.table == table }
            .map { it.formattedId }
            .distinct()
            .takeUnless { it.isEmpty() }
            ?.joinToString(",", "DELETE FROM ${table.tableName} WHERE ${table.formattedIdField} IN (", ");")
}

/**
 * A simple interface that defines a table for the DeleteBuilder.  See example in [DeleteBuilder]
 */
interface Table<IdType, EntityType> {
    val tableName: String
    val formattedIdField: String
    fun recordForId(id: IdType): TableRecord<IdType, EntityType>
    fun recordForEntity(entity: EntityType): TableRecord<IdType, EntityType>

    companion object {
        fun <IdType, EntityType> table(tableName: String, formattedIdField: String): TableBuilder<IdType, EntityType> = TableBuilder(tableName, formattedIdField)
    }
}

/**
 * A context object for building a table using [Table.table].  See [DeleteBuilder] for example.  You have to supply both model and id converters.  It's done like this
 * because something like the following makes nice use of Kotlin's syntactic sugar for lambdas.
 *
 * ```
 * val TENANT_TABLE = Table.table<TenantId, TenantId>("tenant", "tenant_id")
 *     .convertId { "'$it'" }
 *     .convertEntity { throw IllegalStateException("Models not available") }
 *     .build()
 * ```
 */
class TableBuilder<IdType, EntityType>(val tableName: String, val formattedIdField: String) {
    private var idConverter: ((IdType) -> String)? = null
    private var entityConverter: ((EntityType) -> String)? = null

    /**
     * Pass a lambda that is used to convert an id into a string that can be literally inserted into
     * a SQL statement.  E.g., for a string:
     *
     * ```
     * .convertId { "'$it'" }
     * ```
     */
    fun convertId(block: (IdType) -> String): TableBuilder<IdType, EntityType> {
        idConverter = block
        return this
    }

    /**
     * Converts the entity type into the id.  Maybe like:
     *
     * ```
     * .convertEntity { "'${it.id}'" }
     * ```
     */
    fun convertEntity(block: (EntityType) -> String): TableBuilder<IdType, EntityType> {
        entityConverter = block
        return this
    }

    /**
     * Builds the final table def
     */
    fun build(): Table<IdType, EntityType> {
        val idConverter = this.idConverter ?: throw IllegalStateException("Must supply id converter")
        val entityConverter = this.entityConverter ?: throw IllegalStateException("Must supply entity converter")
        return object : Table<IdType, EntityType> {
            override val tableName: String = this@TableBuilder.tableName
            override val formattedIdField: String = this@TableBuilder.formattedIdField

            override fun recordForEntity(entity: EntityType): TableRecord<IdType, EntityType> = TableRecord(
                formattedId = entityConverter(entity),
                table = this
            )

            override fun recordForId(id: IdType): TableRecord<IdType, EntityType> = TableRecord(
                formattedId = idConverter(id),
                table = this
            )
        }
    }
}

/**
 * Used by DeleteBuilder to define the database and its tables in delete order.
 */
interface DatabaseTables {
    val dbName: String
    val tablesInDeleteOrder: List<Table<*, *>>
}

/**
 * Used internally to represent a record to delete
 */
data class TableRecord<IdType, EntityType>(
    val formattedId: String,
    val table: Table<IdType, EntityType>
)
