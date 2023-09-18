plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    implementation(libs.kotlin.logging)
    implementation(libs.micrometer.statsd)
    implementation(libs.bundles.jackson)
    implementation(libs.spring.security.core)
}
