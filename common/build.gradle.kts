plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.micrometer.statsd)
    implementation(libs.datadog.api)
    implementation(libs.opentracing.util)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.kotlin)
}
