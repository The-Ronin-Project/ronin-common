plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))
    api(project(":kafka"))

    testImplementation(libs.kafka.streams.test.utils)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
