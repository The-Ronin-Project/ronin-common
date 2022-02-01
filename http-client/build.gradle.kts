plugins {
    id("com.projectronin.services.gradle.base")
    id("com.projectronin.interop.gradle.junit")
}

dependencies {
    api("io.ktor:ktor-client-core")
    api("io.ktor:ktor-client-cio")
    api("io.ktor:ktor-client-logging")
    api("io.ktor:ktor-client-auth")
    api("io.ktor:ktor-client-serialization")
    api("io.ktor:ktor-client-jackson")

    testImplementation("io.ktor:ktor-client-mock")
}
