# Ronin Services Common Test

This project provides common extension functions and configurations to be used in tests.

These extensions/configurations are for:
- [Kotest](https://mockk.io/)
- [MockK](https://kotest.io/)
- [Spring Boot Starter Test (MockMvc, etc)](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-test)

To use this library, simply include it as a dependency in the consuming project

```kotlin
dependencies {
    testImplementation("com.projectronin.services.common:test")
}
```

then use the extensions to your heart's content.

## Kotest

To use Kotest on top of Spring, we need to leverage their [Spring extension](https://kotest.io/docs/extensions/spring.html).

The [SpringProjectTestConfig](src/main/kotlin/SpringProjectTestConfig.kt) can be used to quickly configure your project's tests.

Just extend the class (in your test directory somewhere) and you're good to go!

```kotlin
class MyModuleConfig : SpringProjectTestConfig()
```

Kotest also uses `TestTag`s to control test filtering.  Since some tags will inevitably be duplicated, we provide some common tags in [CommonTestTags](src/main/kotlin/CommonTestTags.kt)
## MockK

MockK is our mocking framework of choice and we offer some extensions on top of that

- [MockKExtensions](src/main/kotlin/extensions/mockk/MockKExtensions.kt) offers general extensions to the framework
- [MockKVerificationExtensions](src/main/kotlin/extensions/mockk/MockKVerificationExtensions.kt) offers extensions around verifying mocks

For example, to create a relaxed mock:
```kotlin
val objectToMock: MyClass = relaxedMock()
// Alternatively
val objectToMock = relaxedMock<MyClass>()
```

## Spring Starter Test


### MockMvc

MockMvc can get...a bit verbose. To that end, we offer some extensions around creating and verifying

[MockMvcExtensions](src/main/kotlin/extensions/mockmvc/MockMvcExtensions.kt) holds these extensions.

For example, to verify your get request came back with a 200 (OK) and some json:

```kotlin
mockMvc.get("/anendpoint")
    .statusOk()
    .expectJsonContent("{ key: value }")
```