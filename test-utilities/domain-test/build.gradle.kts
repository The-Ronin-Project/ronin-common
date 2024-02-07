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

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.mysql.connector)
    implementation(roningradle.database.test.helpers)
    implementation("org.testcontainers:testcontainers:1.19.4")
    implementation("org.testcontainers:mysql:1.19.4")
    implementation("org.testcontainers:kafka:1.19.4")
    implementation("org.testcontainers:junit-jupiter:1.19.4")
    implementation("org.wiremock.integrations.testcontainers:wiremock-testcontainers-module:1.0-alpha-13")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    implementation("com.hazelcast:hazelcast:5.3.6")
    implementation(project(":common"))
    api(project(":test-utilities:jwt-auth-test"))

    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation("com.projectronin.fhir:common-fhir-r4-models:1.3.0")
    testImplementation("com.projectronin:common-data:4.0.1")
    testImplementation("com.projectronin.contract.event.assets:contract-messaging-assets-v1:1.0.7-v1.0.7")
    testImplementation("com.projectronin.contract.event.tenant:contract-messaging-tenant-v1:1.0.0")
    testImplementation("com.projectronin.rest.contract:contract-rest-document-api-v1:1.0.7")
    testImplementation("com.projectronin.rest.contract:contract-rest-assets-api-v1:1.0.7")
}
