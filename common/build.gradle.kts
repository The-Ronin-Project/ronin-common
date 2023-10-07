plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    implementation(libs.kotlin.logging)
    implementation(libs.micrometer.statsd)
    implementation(libs.datadog.api)
    implementation(libs.opentracing.util)
}
