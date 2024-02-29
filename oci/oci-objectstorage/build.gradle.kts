plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":oci"))
    api(project(":bucketstorage"))
    implementation(libs.ocisdk.objectstorage)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
