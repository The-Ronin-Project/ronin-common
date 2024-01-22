plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.micrometer.statsd)
    implementation(libs.datadog.api)
    implementation(libs.opentracing.util)
    api(libs.resilience4j.retry)
    api(libs.resilience4j.kotlin)
    api(libs.resilience4j.micrometer)
    api(libs.resilience4j.circuitbreaker)
}
