package com.projectronin.contracttest

import com.projectronin.domaintest.resetWiremock
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.HttpURLConnection

/**
 * An example of how to write a local contract test.
 */
@ExtendWith(LocalContractTestExtension::class)
class LocalContractTestSetupTest {

    @BeforeEach
    fun setupMethod() {
        resetWiremock()
    }

    @AfterEach
    fun teardownMethod() {
        resetWiremock()
    }

    @Test
    fun shouldCreateAndRetrieveStudent() = contractTest {
        setSessionToken(jwtAuthToken())
        val studentId = post("/api/student", service = ServiceUnderTest, body = createStudentRequest.toRequestBody("application/json".toMediaType())) {
            defaultToken()
        }.execute(HttpURLConnection.HTTP_CREATED) { response ->
            response.readBodyValue<Map<String, Any>>()["id"].toString()
        }

        assertThat(studentId).isNotNull()

        val student = request("/api/student/$studentId", service = ServiceUnderTest) {
            defaultToken()
        }
            .execute { response ->
                response.readBodyValue<Map<String, Any>>()
            }

        assertThat(student).isNotNull()
        assertThat(student["id"]).isEqualTo(studentId)
        assertThat(student["firstName"]).isEqualTo("William")
        assertThat(student["lastName"]).isEqualTo("Doi")
        assertThat(student["favoriteNumber"]).isEqualTo(17)
        assertThat(student["birthDate"]).isEqualTo("2012-12-07")
        assertThat(student["createdAt"]).isNotNull()
        assertThat(student["updatedAt"]).isNotNull()
    }

    @Test
    fun shouldFailOnBadAuth() = runBlocking {
        coContractTest {
            setSessionToken(invalidJwtAuthToken())
            post("/api/student", body = createStudentRequest.toRequestBody("application/json".toMediaType())) {
                defaultToken()
            }.execute(HttpURLConnection.HTTP_UNAUTHORIZED) {}
        }
    }
}

private val createStudentRequest = """
    {
        "firstName": "William",
        "lastName": "Doi",
        "favoriteNumber": 17,
        "birthDate": "2012-12-07"
    }
""".trimIndent()
