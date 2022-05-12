plugins {
    java
    `maven-publish`
    id("com.projectronin.services.gradle.base") version "1.0.0-SNAPSHOT" apply false
    id("com.projectronin.services.gradle.boot") version "1.0.0-SNAPSHOT" apply false
}

publishing {
    repositories {
        maven {
            name = "nexus"
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_TOKEN")
            }
            url = if (project.version.toString().endsWith("SNAPSHOT")) {
                uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            } else {
                uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            }
        }
    }
}