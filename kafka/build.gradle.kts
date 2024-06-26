plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))
    implementation(libs.bundles.jackson)
    api(libs.kafka.clients)
    api(libs.kafka.streams)
    api(libs.kafka.streams.test.utils)
    implementation(libs.kotlin.logging)
    implementation(libs.spring.core)
    implementation(libs.spring.context)

    implementation(libs.datadog.api)
    implementation(libs.opentracing.util)
    implementation(libs.micrometer.statsd)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
