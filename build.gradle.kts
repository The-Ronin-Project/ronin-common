plugins {
    alias(roningradle.plugins.buildconventions.root)
    alias(roningradle.plugins.buildconventions.versioning)
}

roninSonar {
    coverageExclusions.set(
        listOf(
            "**/test/**",
            "**/test-utilities/**",
            "**/*testutils/**",
            "**/contracttest/**",
            "**/*.kts",
            "**/kotlin/dsl/accessors/**",
            "**/kotlin/test/**"
        )
    )
}
