plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))
    implementation(libs.bundles.jackson)
    api(libs.kafka.clients)
    api(libs.kafka.streams)
    implementation(libs.kotlin.logging)
    implementation(libs.spring.core)
    implementation(libs.spring.context)

    implementation(libs.micrometer.statsd)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
