plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":test-utilities:domain-test"))
    implementation("org.testcontainers:testcontainers:1.19.4")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation(libs.kotlinx.coroutines.core)
}
