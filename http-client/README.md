# Ronin Services HTTP Client

This project provides plugins for [Ktor Client](https://ktor.io/docs/client.html) that facilitate simple, consistent
HTTP clients.

To use this library, simply include it as a dependency in the consuming project

```kotlin
dependencies {
    implementation("com.projectronin.services.common:http-client")
}
```

then create an HttpClient instance as usual, leveraging this library:

```kotlin
fun something() {
    var client = HttpClient {
        whateverPlugin()
        
        // other Ronin HTTP Client plugins
        // or other HttpClient configuration
    }
}
```

## Plugins

### `auth0ClientCredentials`

Configures an `HttpClient` instance to use the OAuth2 client credentials flow with Auth0. Or, in other words, configures
a client for machine to machine authentication.

```kotlin
var client = HttpClient {
    auth0ClientCredentials {
        tokenUrl = "https://something/oauth/token"
        clientId = "..."
        clientSecret = "..."
        audience = "..."
    }
}
```
