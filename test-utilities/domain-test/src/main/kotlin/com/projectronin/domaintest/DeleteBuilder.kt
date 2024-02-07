package com.projectronin.domaintest

interface Table<IdType, EntityType> {
    val tableName: String
    val formattedIdField: String
    fun recordForId(id: IdType): TableRecord<IdType, EntityType>
    fun recordForEntity(entity: EntityType): TableRecord<IdType, EntityType>

    companion object {
        fun <IdType, EntityType> table(tableName: String, formattedIdField: String): TableBuilder<IdType, EntityType> = TableBuilder(tableName, formattedIdField)
    }
}

class TableBuilder<IdType, EntityType>(val tableName: String, val formattedIdField: String) {
    private var idConverter: ((IdType) -> String)? = null
    private var entityConverter: ((EntityType) -> String)? = null
    fun convertId(block: (IdType) -> String): TableBuilder<IdType, EntityType> {
        idConverter = block
        return this
    }

    fun convertEntity(block: (EntityType) -> String): TableBuilder<IdType, EntityType> {
        entityConverter = block
        return this
    }

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

interface DatabaseTables {
    val dbName: String
    val tablesInDeleteOrder: List<Table<*, *>>
}

data class TableRecord<IdType, EntityType>(
    val formattedId: String,
    val table: Table<IdType, EntityType>
)

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
