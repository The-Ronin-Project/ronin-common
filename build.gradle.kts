import com.projectronin.roninbuildconventionsroot.DependencyHelper.Plugins.kotlin

plugins {
    alias(roningradle.plugins.buildconventions.root)
    alias(roningradle.plugins.buildconventions.versioning)
}

roninSonar {
    coverageExclusions.set(
        listOf(
            "**/test/**",
            "**/*testutils/**",
            "**/*.kts",
            "**/kotlin/dsl/accessors/**",
            "**/kotlin/test/**"
        )
    )
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = true
        }
    }
}
