package com.projectronin.domaintest

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.projectronin.auth.token.RoninLoginProfile
import com.projectronin.common.TenantId
import com.projectronin.domaintest.serviceproviders.AssetsDatabaseTables
import com.projectronin.domaintest.serviceproviders.assetsDeleteBuilder
import com.projectronin.event.assets.v1.AssetSchema
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection

@ExtendWith(DomainTestExtension::class)
class DomainTestTest {

    @Test
    fun `should allow building a new http client and removing it`() = runBlocking {
        coDomainTest {
            var intercepted: Boolean = false

            buildHttpClient(OkHttpClient.Builder()) {
                addInterceptor {
                    intercepted = true
                    it.proceed(it.request())
                }
            }

            request("auth", "/actuator/info").execute {}
            assertThat(intercepted).isTrue()

            intercepted = false

            clearHttpClient()
            request("auth", "/actuator/info").execute {}
            assertThat(intercepted).isFalse()
        }
    }

    @Test
    fun `should successfully translate a body`() = domainTest {
        val assetSchema = AssetSchema().apply {
            tenantId = "tenant"
            patientId = "patient"
            resourceType = "foo"
        }

        val body = requestBody(assetSchema) { node ->
            node.removeObjectField("patientId")
        }

        assertThat(body.contentType().toString()).isEqualTo("application/json; charset=utf-8")
        Buffer().use { buf ->
            body.writeTo(buf)
            ByteArrayOutputStream().use { os ->
                buf.writeTo(os)
                val outputSchema = objectMapper.readValue<AssetSchema>(os.toByteArray())
                assertThat(outputSchema.tenantId).isEqualTo("tenant")
                assertThat(outputSchema.patientId).isNull()
                assertThat(outputSchema.resourceType).isEqualTo("foo")
            }
        }
    }

    @Test
    fun `should execute and verify a bad request missing field`() = domainTest {
        val badRequest = request()
            .gatewayPost(
                path = "/api/v1/tenants/apposnd/patients/apposnd-94xNAMvgsMWzJAh8vEhR9vSioXkGn5/assets",
                body = requestBody(
                    AssetSchema().apply {
                        tenantId = "tenant"
                        patientId = "patient"
                        resourceType = "foo"
                    }
                )
            )
            .configure {
                bearerAuthorization(
                    jwtAuthToken {
                        withLoginProfile(
                            RoninLoginProfile(
                                accessingTenantId = "apposnd",
                                accessingPatientUdpId = "apposnd-94xNAMvgsMWzJAh8vEhR9vSioXkGn5",
                                accessingProviderUdpId = null,
                                accessingExternalPatientId = null
                            )
                        )
                    }
                )
                header("Accept", "application/json")
            }
            .executeBadRequest()
        badRequest.verifyMissingRequiredField("filename")
    }

    @Test
    fun `should execute and verify a bad request invalid field value`() = domainTest {
        val badRequest = request()
            .servicePost(
                service = KnownServices.Assets,
                path = "/api/v1/tenants/apposnd/patients/apposnd-94xNAMvgsMWzJAh8vEhR9vSioXkGn5/assets",
                body = """
                    {
                      "filename": "String",
                      "md5": "String",
                      "resourceId": "String",
                      "resourceAttribute": "String",
                      "data": "ByteArray"
                      "resourceType": "baz"
                    }
                """.trimIndent().toRequestBody("application/json".toMediaType())
            )
            .configure {
                bearerAuthorization(
                    jwtAuthToken {
                        withLoginProfile(
                            RoninLoginProfile(
                                accessingTenantId = "apposnd",
                                accessingPatientUdpId = "apposnd-94xNAMvgsMWzJAh8vEhR9vSioXkGn5",
                                accessingProviderUdpId = null,
                                accessingExternalPatientId = null
                            )
                        )
                    }
                )
                header("Accept", "application/json")
            }
            .executeBadRequest()
        badRequest.verifyInvalidFieldValue("data")
    }

    @Test
    fun `should write, read, and cleanup db values`() = domainTest {
        val tenantId = TenantId.random()
        withDatabase("assets") {
            cleanupWithDeleteBuilder(assetsDeleteBuilder()) { cleanup ->

                cleanup += AssetsDatabaseTables.TENANT_TABLE.recordForId(tenantId)

                executeUpdate("INSERT INTO tenant (tenant_id, bucket_name) VALUES ('$tenantId', 'bar')")
                val count = executeQuery("SELECT count(1) as 'tenant_count' FROM tenant WHERE tenant_id = '$tenantId'") {
                    assertThat(it.next()).isTrue()
                    it.getInt("tenant_count")
                }

                assertThat(count).isEqualTo(1)
            }
            val count = executeQuery("SELECT count(1) as 'tenant_count' FROM patient_alias WHERE tenant_id = '$tenantId'") {
                assertThat(it.next()).isTrue()
                it.getInt("tenant_count")
            }

            assertThat(count).isEqualTo(0)
        }
    }

    @Test
    fun `should do some redirects`() = domainTest {
        stubFor(
            get(urlEqualTo("/path/1"))
                .willReturn(
                    aResponse()
                        .withStatus(302)
                        .withHeader("Location", externalWiremockUrl("/path/2"))
                )
        )
        stubFor(
            get(urlEqualTo("/path/2"))
                .willReturn(
                    aResponse()
                        .withStatus(302)
                        .withHeader("Location", externalWiremockUrl("/path/3"))
                )
        )
        stubFor(
            get(urlEqualTo("/path/3"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"ok": true}""")
                )
        )

        buildHttpClient { followRedirects(false) }

        request()
            .get(externalWiremockUrl("/path/1"))
            .chainRedirects {
                assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/2"))
            }
            .then("On to the second path") {
                assertThat(it.header("Location")).isEqualTo(externalWiremockUrl("/path/3"))
            }
            .then("On to the third path", expectedStatus = HttpURLConnection.HTTP_OK) {
                assertThat(it.header("Content-Type")).isEqualTo("application/json")
                assertThat(it.body?.string()).isEqualTo("""{"ok": true}""")
            }
    }

    @Test
    fun `should expose a debugging port`() {
        assertThat(exposedServicePort(KnownServices.DocumentApi, 5005)).isGreaterThan(0)
    }

    @Test
    fun `should correctly manage DB additions`() {
        // this one should be fine
        MySQLServiceContext.instance.withDatabase("document_api")
        assertThatThrownBy { MySQLServiceContext.instance.withDatabase("document_api", "foo") }
            .isInstanceOf(AssertionError::class.java)
        assertThatThrownBy { MySQLServiceContext.instance.withDatabase("foo", "assets") }
            .isInstanceOf(AssertionError::class.java)
    }
}
