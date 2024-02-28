plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(libs.assertj)
    api(libs.jackson.kotlin)
    api(libs.kafka.clients)
    api(libs.logback.classic)
    api(libs.logback.core)
    api(libs.okhttp)
    api(libs.slf4j.api)
    api(libs.wiremock)
    api(project(":kafka-test"))
    api(libs.bundles.retry)
    api(project(":test-utilities:jwt-auth-test"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.mysql.connector)
    implementation(roningradle.database.test.helpers)
    implementation(project(":common"))
    implementation(libs.bundles.testcontainers)
    implementation(libs.junit.api)
    implementation(libs.hazelcast)
    implementation(libs.classgraph)
    implementation("org.jacoco:org.jacoco.core:0.8.11")

    compileOnly(libs.jetbrains.annotations)

    // These are left as strings because they're not essential dependencies of ronin-common, and can remain fixed
    // here, and don't need to be published in the catalog
    testImplementation("com.projectronin.fhir:common-fhir-r4-models:1.3.0")
    testImplementation("com.projectronin:common-data:4.0.1")
    testImplementation("com.projectronin.contract.event.assets:contract-messaging-assets-v1:1.0.7-v1.0.7")
    testImplementation("com.projectronin.contract.event.tenant:contract-messaging-tenant-v1:1.0.0")
    testImplementation("com.projectronin.rest.contract:contract-rest-document-api-v1:1.0.7")
    testImplementation("com.projectronin.rest.contract:contract-rest-assets-api-v1:1.0.7")
}
