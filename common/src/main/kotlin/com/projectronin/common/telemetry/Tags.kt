package com.projectronin.common.telemetry

object Tags {
    const val TENANT_TAG = "ronin.tenant"
    const val PATIENT_TAG = "ronin.patient"

    const val KAFKA_TOPIC_TAG = "kafka.topic"
    const val KAFKA_PARTITION_TAG = "kafka.partition"
    const val KAFKA_OFFSET_TAG = "kafka.offset"

    const val RONIN_EVENT_ID_TAG = "ronin.event.id"
    const val RONIN_EVENT_SOURCE_TAG = "ronin.event.source"
    const val RONIN_EVENT_TYPE_TAG = "ronin.event.type"
    const val RONIN_EVENT_VERSION_TAG = "ronin.event.version"

    const val RONIN_AUTH_IS_AUTHENTICATED = "ronin.auth.is_authenticated"
    const val RONIN_AUTH_USER_TYPE = "ronin.auth.user_type"
    const val RONIN_AUTH_USER_ID = "ronin.auth.user_id"
    const val RONIN_AUTH_PATIENT_ID = "ronin.auth.session.patient_id"
    const val RONIN_AUTH_PROVIDER_ID = "ronin.auth.session.provider_id"
}
