plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    implementation(libs.bundles.jackson)
    implementation(libs.spring.security.core)
}
