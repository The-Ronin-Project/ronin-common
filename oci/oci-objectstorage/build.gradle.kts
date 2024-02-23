plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":oci"))
    implementation(project(":bucketstorage"))

    api("com.oracle.oci.sdk:oci-java-sdk-objectstorage")

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
