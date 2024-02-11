# jwt-auth-tests

A collection of helpers for constructing JWT tokens for use in testing Ronin services.  They are mostly useful in conjunction with WireMock or with the auth service in domain or local
contract tests.

The classes should all be documented, so see them for examples.  And see the tests in this module.

Typical usage is going to be _through_ the interfaces in a domain test, as in [SomethingAuthorizedTest](../domain-test/src/test/kotlin/com/projectronin/domaintest/SomethingAuthorizedTest.kt).

For example:

```kotlin
domainTest {
    setSessionToken(
        jwtAuthToken {
            withScopes("admin:read")
        }
    )
    // make some requests
}
```

Outside of domain tests, you're going to use something closer to this:

```kotlin
class SomeSomethingTest {

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun setup() {
            wireMockServer.start()
            configureFor(wireMockServer.port())
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            wireMockServer.stop()
        }
    }

    @Test
    fun `should test some something`() {
        val key = generateRandomRsa()

        withAuthWiremockServer(key, wireMockServer.baseUrl()) {
            val m2mToken = jwtAuthToken {
                withScopes("admin:read")
            }
        }
    }
}
```
