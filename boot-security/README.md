# Ronin Boot Security

Spring Boot plugin that provides autoconfiguration of common security functionality.

To use this plugin, add it to your Gradle file, then configure each module that is desired.

## Gradle example

```kotlin
dependencies {
    implementation("com.projectronin.services.common.boot-security")
}
```

### Modules

## Machine to Machine authentication

To enable machine to machine authentication in an application, use the following configuration:

```yaml
ronin:
  server:
    auth:
      m2m:
        enabled: true
        issuer: "..."
        audience: "..."
```

Note that in general services should put the `enabled` flag in `src/main/resources/application.yml` so that M2M auth is
always enabled. Otherwise, there's a risk that enabling it could be forgotten at deploy time.

As usual, environmental variables can also be used to provide configuration:

```shell
export RONIN_SERVICE_AUTH_M2M_ISSUER="..."
```
