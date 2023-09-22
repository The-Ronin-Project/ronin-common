plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":auth"))
    implementation(libs.bundles.jackson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
