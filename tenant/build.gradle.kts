plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":kafka"))
    api(libs.contract.messaging.tenant.v1)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.jackson)
    implementation(libs.kafka.streams)
    implementation(libs.kafka.streams.test.utils)
    implementation(libs.kotlin.logging)

    implementation(libs.datadog.api)

    testImplementation(libs.assertj)
    testImplementation(libs.mockk)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
