plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":kafka"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.jackson)
    implementation(libs.contract.messaging.tenant.v1)
    api(libs.kafka.clients)
    api(libs.kafka.streams)
    api(libs.kafka.streams.test.utils)
    implementation(libs.kotlin.logging)

    implementation(libs.datadog.api)

    testImplementation(libs.assertj)
    testImplementation(libs.mockk)
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
