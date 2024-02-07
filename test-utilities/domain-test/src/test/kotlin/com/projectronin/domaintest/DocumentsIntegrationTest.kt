package com.projectronin.domaintest

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.projectronin.auth.token.RoninLoginProfile
import com.projectronin.common.TenantId
import com.projectronin.domaintest.serviceproviders.AssetsDatabaseTables
import com.projectronin.domaintest.serviceproviders.DocumentsServicesProvider
import com.projectronin.event.assets.v1.AssetSchema
import com.projectronin.fhir.r4.Attachment
import com.projectronin.fhir.r4.CodeableConcept
import com.projectronin.fhir.r4.Coding
import com.projectronin.fhir.r4.DocumentReference
import com.projectronin.fhir.r4.DocumentReference_Content
import com.projectronin.fhir.r4.Identifier
import com.projectronin.fhir.r4.Reference
import com.projectronin.json.tenant.v1.Organization
import com.projectronin.json.tenant.v1.TenantV1Schema
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.serialization.RoninEventSerializer
import com.projectronin.rest.restassetsapi.v1.models.PatientAssetGetResponse
import com.projectronin.rest.restdocumentapi.v1.models.AssetKind
import com.projectronin.rest.restdocumentapi.v1.models.Document
import com.projectronin.test.kafka.assertProduces
import com.projectronin.test.kafka.topic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

@ExtendWith(DomainTestExtension::class)
class DocumentsIntegrationTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        resetWiremock()
    }

    @Test
    fun `should be able to receive a document update and retrieve document and asset`() = domainTest {
        val tenantId = TenantId.random()

        setSessionToken(
            jwtAuthToken {
                withScopes("admin:read")
                withLoginProfile(RoninLoginProfile(accessingTenantId = tenantId.toString(), accessingProviderUdpId = null, accessingPatientUdpId = null, accessingExternalPatientId = null))
            }
        )

        val imageContents = javaClass.classLoader.getResourceAsStream("code.jpg")!!.readAllBytes()
        stubFor(
            get(urlEqualTo("/assets/$tenantId-code.jpg"))
                .willReturn(
                    aResponse()
                        .withBody(imageContents)
                )
        )

        val referenceDate = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val documentEvent = RoninEvent(
            dataSchema = "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/DocumentReference-v1.schema.json",
            source = "ehr-data-authority",
            type = "ronin.ehr-data-authority.document-reference.create",
            data = DocumentReference().apply {
                this.id = "documentId2"
                this.resourceType = "DocumentReference"
                this.identifier = listOf(
                    Identifier().apply {
                        this.system = "http://projectronin.com/id/tenantId"
                        this.value = tenantId.value
                    }
                )
                this.subject = Reference().apply {
                    this.type = "Patient"
                    this.reference = "Patient/patientId"
                }
                this.content = listOf(
                    DocumentReference_Content().apply {
                        this.attachment = Attachment().apply {
                            this.url = "http://wiremock:8080/assets/$tenantId-code.jpg"
                            this.contentType = "image/jpg"
                        }
                    }
                )
                this.type = CodeableConcept().apply {
                    coding = listOf(
                        Coding().apply {
                            system = "http://loinc.org"
                            code = "11506-3"
                            display = "Picture of code"
                        }
                    )
                }
                this.date = referenceDate.toString()
            }
        )

        withKafka {
            producer(
                DocumentsServicesProvider.tenantTopic,
                RoninEventSerializer<TenantV1Schema>()
            ).use { producer ->
                producer.send(
                    "ronin.tenant.tenant.create/${tenantId.value}",
                    RoninEvent(
                        dataSchema = "https://github.com/projectronin/contract-messaging-tenant/blob/main/src/main/resources/schemas/tenant-v1.schema.json",
                        source = "ronin-tenant",
                        type = "ronin.tenant.tenant.create",
                        tenantId = tenantId,
                        data = TenantV1Schema().apply {
                            this.id = tenantId.value
                            this.shortName = ""
                            this.name = ""
                            this.status = TenantV1Schema.TenantStatus.ACTIVE
                            this.organization = Organization()
                            this.clientId = UUID.randomUUID()
                            this.clientAccess = true
                            this.issUrl = ""
                            this.defaultTimezone = ""
                            this.createdAt = Date.from(Instant.now())
                            this.createdBy = ""
                            this.updatedAt = Date.from(Instant.now())
                            this.updatedBy = ""
                        }
                    )
                )
            }

            withDatabase("assets") {
                retryDefault(240, Duration.ofMillis(500), { !it }) {
                    executeQuery("SELECT * FROM tenant t WHERE t.tenant_id = '$tenantId'") {
                        it.next()
                    }
                }.getOrThrow()
            }

            assertProduces<AssetSchema>(
                kafkaExternalBootstrapServers,
                topic(DocumentsServicesProvider.assetsEventTopic) {
                    deserializationType<AssetSchema>("ronin.prodeng-assets.asset.create")
                }
            ) {
                producer(
                    topic = DocumentsServicesProvider.ehrdaDocumentReferenceTopic,
                    valueSerializer = RoninEventSerializer<DocumentReference>()
                ).use {
                    it.send(
                        key = "ronin.ehr-data-authority.document-reference/documentId2",
                        message = documentEvent
                    )
                }
            }
                .also { consumerRecords ->
                    assertThat(consumerRecords.first().value().data.errors).isEmpty()
                }
        }

        val assetId: UUID = retryAssertion(240, Duration.ofMillis(500)) {
            val assetPath = request("document-api", "/api/v1/tenants/$tenantId/patients/patientId/documents/documentId2")
                .defaultToken()
                .execute {
                    val body = it.readBodyValue<Document>()
                    assertThat(body).isNotNull
                    assertThat(body.id).isEqualTo("documentId2")
                    assertThat(body.name).isNull()
                    assertThat(body.author).isNull()
                    assertThat(body.type).isEqualTo("Progress Notes")
                    assertThat(body.assetKind).isEqualTo(AssetKind.JPG)
                    assertThat(body.assetUrl).matches("/api/v1/tenants/$tenantId/patients/patientId/assets/.*")
                    assertThat(body.createdAt.toInstant()).isEqualTo(referenceDate)
                    body.assetUrl
                }
            request("assets", "$assetPath?withData=true")
                .defaultToken()
                .execute {
                    val body = it.body.readValue<PatientAssetGetResponse>()
                    assertThat(body).isNotNull
                    assertThat(body.tenantId).isEqualTo(tenantId.toString())
                    assertThat(body.patientId).isEqualTo("patientId")
                    assertThat(body.md5).isEqualTo(imageContents.md5)
                    assertThat(body.resourceType).isEqualTo("DocumentReference")
                    assertThat(body.resourceId).isEqualTo("documentId2")
                    assertThat(body.resourceAttribute).startsWith("Attachment/")
                    assertThat(body.contentType).isEqualTo("image/jpg")
                    assertThat(body.data).isEqualTo(imageContents)
                }
            UUID.fromString(assetPath.replace(".*/".toRegex(), ""))
        }.getOrThrow()

        withDatabase(AssetsDatabaseTables) {
            cleanupWithDeleteBuilder { cleanup ->

                cleanup += AssetsDatabaseTables.TENANT_TABLE.recordForId(tenantId)

                val tenantCount = executeQuery("SELECT count(1) as 'tenant_count' FROM tenant WHERE tenant_id = '$tenantId'") {
                    assertThat(it.next()).isTrue()
                    it.getInt("tenant_count")
                }
                assertThat(tenantCount).isEqualTo(1)

                val assetCount = executeQuery("SELECT count(1) as 'asset_count' FROM patient_asset WHERE id = '$assetId'") {
                    cleanup += AssetsDatabaseTables.PATIENT_ASSETS_TABLE.recordForId(assetId)
                    assertThat(it.next()).isTrue()
                    it.getInt("asset_count")
                }
                assertThat(assetCount).isEqualTo(1)

                val aliasCount = executeQuery("SELECT tenant_id, patient_id FROM patient_alias WHERE tenant_id = '$tenantId' AND patient_id = 'patientId'") {
                    var c = 0
                    while (it.next()) {
                        c += 1
                        cleanup += AssetsDatabaseTables.PATIENT_ALIAS_TABLE.recordForId(Pair(TenantId(it.getString("tenant_id")), it.getString("patient_id")))
                    }
                    c
                }
                assertThat(aliasCount).isEqualTo(1)
            }

            val tenantCount = executeQuery("SELECT count(1) as 'tenant_count' FROM tenant WHERE tenant_id = '$tenantId'") {
                assertThat(it.next()).isTrue()
                it.getInt("tenant_count")
            }
            assertThat(tenantCount).isEqualTo(0)

            val assetCount = executeQuery("SELECT count(1) as 'asset_count' FROM patient_asset") {
                assertThat(it.next()).isTrue()
                it.getInt("asset_count")
            }
            assertThat(assetCount).isEqualTo(0)

            val aliasCount = executeQuery("SELECT count(1) as 'alias_count' FROM patient_alias") {
                assertThat(it.next()).isTrue()
                it.getInt("alias_count")
            }
            assertThat(aliasCount).isEqualTo(0)
        }
    }

    val ByteArray.md5: String
        get() {
            val md = MessageDigest.getInstance("MD5")
            return Base64.getEncoder().encodeToString(md.digest(this))
        }
}
