plugins {
    id("com.projectronin.services.gradle.base")
    id("com.projectronin.interop.gradle.junit")
}

extra["kotin-coroutines.version"] = "1.6.0"
extra["kotlin.version"] = "1.6.10"

// TODO: Remove this once test dependencies are in base plugin
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    api("io.kotest:kotest-runner-junit5:5.1.0")
    api("io.kotest:kotest-assertions-core:5.1.0")
    api("io.kotest.extensions:kotest-extensions-spring:1.1.0")
    api("org.springframework.boot:spring-boot-starter-test:2.6.3")
    api("io.mockk:mockk:1.12.2")
}
