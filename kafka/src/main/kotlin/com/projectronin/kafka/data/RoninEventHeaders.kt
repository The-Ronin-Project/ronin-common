package com.projectronin.kafka.data

object RoninEventHeaders {
    const val ID = "ce_id"
    const val TIME = "ce_time"
    const val VERSION = "ce_specversion"
    const val DATA_SCHEMA = "ce_dataschema"
    const val CONTENT_TYPE = "content-type"
    const val SOURCE = "ce_source"
    const val TENANT_ID = "ronin_tenant_id"
    const val PATIENT_ID = "ronin_patient_id"
    const val TYPE = "ce_type"
    const val SUBJECT = "ce_subject"

    val required = listOf(
        ID,
        TIME,
        VERSION,
        SOURCE,
        CONTENT_TYPE,
        DATA_SCHEMA,
        TYPE
    )
}
