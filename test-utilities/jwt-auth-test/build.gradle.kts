plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(libs.nimbus.jose.jwt)
    implementation(libs.jackson.kotlin)
    implementation(libs.wiremock)
    api(project(":auth"))
}
