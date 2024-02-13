plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":test-utilities:domain-test"))
    implementation(libs.bundles.testcontainers)
    implementation(libs.junit.api)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.logging)
}
