plugins {
    alias(roningradle.plugins.buildconventions.kotlin.library)
}

dependencies {
    api(project(":common"))

    api(platform("com.oracle.oci.sdk:oci-java-sdk-bom:3.11.0"))
    api("com.oracle.oci.sdk:oci-java-sdk-common")
}
