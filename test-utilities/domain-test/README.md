# Domain Test Framework

This module creates a domain test framework for starting a set of services that work together and testing them.

Check out the code, starting with [DomainTestServicesProvider](src/main/kotlin/com/projectronin/domaintest/DomainTestServicesProvider.kt),
[DomainTestSetupContext](src/main/kotlin/com/projectronin/domaintest/DomainTestSetupContext.kt), and [DomainTest](src/main/kotlin/com/projectronin/domaintest/DomainTest.kt).

Generally, you would set up a domain test suite by creating a new GitHub project from `ronin-domain-test-blueprint`.  Or from scratch, using a settings.gradle.kts like:

```kotlin
rootProject.name = "some-integration-test-project"

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
            from("com.projectronin.services.gradle:ronin-gradle-catalog:2.1.7")
        }
    }
}
```

And a build.gradle.kts like:

```kotlin
plugins {
    alias(roningradle.plugins.buildconventions.kotlin.jvm)
}

dependencies {
    testImplementation(ronincommon.domain.test)
    // add any additional dependencies here
}
```

In your `src/test/kotlin`, place a package related to your domain test, and create an instance of DomainTestServicesProvider.  See
[DocumentsServicesProvider](src/test/kotlin/com/projectronin/domaintest/serviceproviders/DocumentsServicesProvider.kt) for a complete example.  The DomainTestServicesProvider is searched for
by the test extension, and bootstraps all your services.

Your tests you can basically just write like this:

```kotlin
@ExtendWith(DomainTestExtension::class)
class SomeKindOfTest {

    @Test
    fun `should do something`() = domainTest {
        // do some stuff based on the members of domainTest.
    }
}
```

You can see the examples in [domaintest](src/test/kotlin/com/projectronin/domaintest) for worked examples, especially
[DocumentsIntegrationTest.kt](src/test/kotlin/com/projectronin/domaintest/DocumentsIntegrationTest.kt).
