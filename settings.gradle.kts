rootProject.name = "ronin-common-root"

// libraries
include(":common")
include(":kafka")
include(":kafka-test")
include(":auth")
include(":auth:auth-m2m-client")
include(":tenant")
include(":test-utilities:jwt-auth-test")
include(":test-utilities:domain-test")
include(":test-utilities:local-contract-test")

// catalog
include(":ronin-common-catalog")

findProject(":ronin-common-catalog")?.name = "ronin-common"

pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
        }
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
        }
        mavenLocal()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("roningradle") {
            from("com.projectronin.services.gradle:ronin-gradle-catalog:2.3.12")
        }
    }
}
