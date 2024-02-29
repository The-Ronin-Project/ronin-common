plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))

    api(platform(libs.ocisdk.bom))
    api(libs.ocisdk.common)
}
