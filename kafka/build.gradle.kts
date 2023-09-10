plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))
    implementation(libs.jackson)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.module.parameterNames)
    api(libs.kafka)
    api(libs.kafka.streams)
    implementation(libs.kotlin.logging)
    implementation(libs.spring.core)
    implementation(libs.spring.context)

    implementation(libs.micrometer.statsd)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
