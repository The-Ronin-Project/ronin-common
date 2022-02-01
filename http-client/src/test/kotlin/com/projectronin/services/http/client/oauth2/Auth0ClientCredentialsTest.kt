package com.projectronin.services.http.client.oauth2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.nio.charset.Charset

class Auth0ClientCredentialsTest {
    private val successMock = MockEngine { _ ->
        respond(
            content = ByteReadChannel("hello"),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "text/plain")
        )
    }

    @Test
    fun `errors without tokenUrl`() {
        val missingException = assertThrows<IllegalArgumentException> {
            HttpClient {
                auth0ClientCredentials {
                    clientId = "client_id"
                    clientSecret = "client_secret"
                    audience = "audience"
                }
            }
        }
        assertEquals("tokenUrl is required", missingException.message)

        val blankException = assertThrows<IllegalArgumentException> {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = ""
                    clientId = "client_id"
                    clientSecret = "client_secret"
                    audience = "audience"
                }
            }
        }
        assertEquals("tokenUrl is required", blankException.message)
    }

    @Test
    fun `errors without clientId`() {
        val missingException = assertThrows<IllegalArgumentException> {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientSecret = "client_secret"
                    audience = "audience"
                }
            }
        }
        assertEquals("clientId is required", missingException.message)

        val blankException = assertThrows<IllegalArgumentException> {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientId = ""
                    clientSecret = "client_secret"
                    audience = "audience"
                }
            }
        }
        assertEquals("clientId is required", blankException.message)
    }

    @Test
    fun `errors without clientSecret`() {
        assertThrows<IllegalArgumentException> {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientId = "client_id"
                    audience = "audience"
                }
            }
        }

        assertThrows<IllegalArgumentException>() {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientId = "client_id"
                    clientSecret = ""
                    audience = "audience"
                }
            }
        }
    }

    @Test
    fun `allows empty audience`() {
        assertDoesNotThrow {
            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientId = "client_id"
                    clientSecret = "client_secret"
                    audience = ""
                }
            }

            HttpClient {
                auth0ClientCredentials {
                    tokenUrl = "https://authserver.local/token"
                    clientId = "client_id"
                    clientSecret = "client_secret"
                }
            }
        }
    }

    @Test
    fun `calls auth0 for token and passes that through for request`() {
        val auth0Engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                        "access_token": "abcdef",
                        "expires_in": "3600",
                        "token_type": "Bearer"
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(successMock) {
            auth0ClientCredentials {
                tokenUrl = "https://authserver.local/token"
                clientId = "client id"
                clientSecret = "client secret"
                audience = "aud"
                auth0ClientEngine = auth0Engine
            }
        }

        runBlocking {
            val response = client.post<String>("https://service.local")

            assertEquals(1, auth0Engine.requestHistory.size)

            val auth0Request = auth0Engine.requestHistory.last()
            val auth0RequestBody = String(auth0Request.body.toByteArray(), Charset.forName("UTF-8"))

            assertEquals("https://authserver.local/token", auth0Request.url.toString())
            assertEquals(HttpMethod.Post, auth0Request.method)
            assertEquals(ContentType.Application.Json, auth0Request.body.contentType)
            assertEquals(
                """{"client_id":"client id","client_secret":"client secret","audience":"aud","grant_type":"client_credentials"}""",
                auth0RequestBody
            )

            assertEquals("hello", response)
            assertEquals("Bearer abcdef", successMock.requestHistory.last().headers["Authorization"])
        }
    }

    @Test
    fun `refreshes token on 401 response with WWW-Authenticate`() {
        var auth0CallCount = 0
        val auth0Engine = MockEngine { _ ->
            val token = if (auth0CallCount++ == 0) "accesstoken1" else "accesstoken2"

            respond(
                content = ByteReadChannel(
                    """
                    {
                        "access_token": "$token",
                        "expires_in": "3600",
                        "token_type": "Bearer"
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        var apiCallCount = 0
        val apiEngine = MockEngine { request ->
            apiCallCount += 1

            // simulate accesstoken1 being valid on the first call, then subsequent calls needing the "refreshed" accesstoken2
            val status = if (apiCallCount == 1) {
                if (request.headers["Authorization"] == "Bearer accesstoken1") HttpStatusCode.OK else HttpStatusCode.Unauthorized
            } else {
                if (request.headers["Authorization"] == "Bearer accesstoken2") HttpStatusCode.OK else HttpStatusCode.Unauthorized
            }

            val headers = if (status == HttpStatusCode.Unauthorized) {
                headersOf(
                    HttpHeaders.ContentType to listOf("text/plain"),
                    HttpHeaders.WWWAuthenticate to listOf("Bearer")
                )
            } else {
                headersOf(HttpHeaders.ContentType, "text/plain")
            }

            respond(content = "", status = status, headers = headers)
        }

        val client = HttpClient(apiEngine) {
            auth0ClientCredentials {
                tokenUrl = "https://authserver.local/token"
                clientId = "client id"
                clientSecret = "client secret"
                audience = "aud"
                auth0ClientEngine = auth0Engine
            }
        }

        runBlocking {
            client.post<String>("https://service.local")
            assertEquals("Bearer accesstoken1", apiEngine.requestHistory.last().headers["Authorization"])

            runCatching {
                client.post<String>("https://service.local")
            }

            assertEquals(2, auth0Engine.requestHistory.size)
            assertEquals(3, apiEngine.requestHistory.size)
            assertEquals("Bearer accesstoken1", apiEngine.requestHistory.first().headers["Authorization"])
            assertEquals("Bearer accesstoken2", apiEngine.requestHistory.last().headers["Authorization"])
        }
    }
}
