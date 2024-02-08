package com.projectronin.domaintest.serviceproviders

import com.projectronin.domaintest.DomainTestServicesProvider
import com.projectronin.domaintest.DomainTestSetupContext
import com.projectronin.domaintest.internalJdbcUrlFor
import com.projectronin.domaintest.internalOidcIssuer
import com.projectronin.domaintest.kafkaInternalBootstrapServers
import com.projectronin.domaintest.oidcIssuer

class DocumentsServicesProvider : DomainTestServicesProvider {

    companion object {
        val ehrdaDocumentReferenceTopic = "oci.us-phoenix-1.ehr-data-authority.document-reference.v1"

        val assetsEventTopic = "oci.us-phoenix-1.prodeng-assets.asset-events.v1"
        val assetsCommandTopic = "oci.us-phoenix-1.prodeng-assets.asset-commands.v1"

        val documentsDlqTopic = "oci.us-phoenix-1.documents.dlq"

        val documentEventsTopic = "oci.us-phoenix-1.document-data.document-events.v1"

        val tenantTopic = "oci.us-phoenix-1.ronin-tenant.tenant.v1"

        val tempFhirEventsTopic = "oci.us-phoenix-1.document-data.temp-fhir-events.v1"
    }

    override fun configurer(): DomainTestSetupContext.() -> Unit {
        return {
            withMySQL()
            withKafka()
            withWireMock {
                withM2MSupport()
            }
            withAuth("1.0.39")
            withProductEngineeringService("document-api", "document-api-service", "2.0.16") {
                dependsOnMySQLDatabase("document_api")
                dependsOnKafka(
                    documentEventsTopic,
                    documentsDlqTopic,
                    tenantTopic
                )
                dependsOnWireMock()
                configYaml(
                    """
                    spring:
                      config:
                        import: classpath:application.yml
                    ---
                    spring:
                      datasource:
                        url: ${internalJdbcUrlFor("document_api")}
                      liquibase:
                        url: ${internalJdbcUrlFor("document_api")}
                        enabled: true
                    ronin:
                      auth:
                        issuers:
                          - http://auth:8080
                          - ${oidcIssuer()}
                      product:
                        document-api:
                          topic-document-events: "$documentEventsTopic"
                          topic-dlq: "$documentsDlqTopic"
                          topic-tenants: "$tenantTopic"
                          default-page-limit: 2
                          registry-uuid: "765b49c5-dff6-4fd9-9809-4c03fd9beb3a"
                          registry-version: 4
                      kafka:
                        bootstrap-servers: $kafkaInternalBootstrapServers
                        security-protocol: PLAINTEXT
                    """.trimIndent()
                )
            }
            withProductEngineeringService("document-data", "document-data-service", "2.0.12") {
                dependsOnMySQLDatabase("document_data")
                dependsOnKafka(
                    assetsEventTopic,
                    ehrdaDocumentReferenceTopic,
                    documentsDlqTopic,
                    tenantTopic,
                    documentEventsTopic,
                    assetsCommandTopic,
                    tempFhirEventsTopic
                )
                configYaml(
                    """
                    spring:
                      config:
                        import: classpath:application.yml
                    ---
                    spring:
                      datasource:
                        url: ${internalJdbcUrlFor("document_data")}
                      liquibase:
                        enabled: true
                      r2dbc:
                        url: ${internalJdbcUrlFor("document_data").replace("jdbc:", "r2dbc:")}
                    ronin:
                      auth:
                        issuers:
                          - http://auth:8080
                          - ${oidcIssuer()}
                      kafka:
                        bootstrap-servers: $kafkaInternalBootstrapServers
                        security-protocol: PLAINTEXT
                      product:
                        document-data:
                          topic-asset-events: "$assetsEventTopic"
                          topic-ehr-document-events: "$ehrdaDocumentReferenceTopic"
                          topic-dlq: "$documentsDlqTopic"
                          topic-tenant-events: "$tenantTopic"
                          base-ehr-url: "http://localhost"
                          topic-document-events: "$documentEventsTopic"
                          topic-asset-commands: "$assetsCommandTopic"
                          topic-temp-ehr-document-events: "$tempFhirEventsTopic"
                    """.trimIndent()
                )
            }
            withProductEngineeringService("assets", "assets-service", "2.0.12") {
                dependsOnMySQLDatabase("assets")
                dependsOnKafka(
                    assetsCommandTopic,
                    assetsEventTopic,
                    tenantTopic,
                    documentsDlqTopic
                )
                dependsOnWireMock()
                configYaml(
                    """
                    spring:
                      config:
                        import: classpath:application.yml
                    ---
                    spring:
                      datasource:
                        url: ${internalJdbcUrlFor("assets")}
                      liquibase:
                        enabled: true
                      profiles:
                        active: local
                    ronin:
                      auth:
                        issuers:
                          - http://auth:8080
                          - ${oidcIssuer()}
                      product:
                        assets:
                          env: test
                          ehr-m2-m-audience: http://localhost/
                        audit:
                          sourceService: assets
                          enabled: false
                        kafka:
                          stream:
                            command-topic: $assetsCommandTopic
                            event-topic: $assetsEventTopic
                            tenant-topic: $tenantTopic
                            application-id: prodeng-assets.asset-events.v1
                            dlq-topic: $documentsDlqTopic
                      kafka:
                        bootstrap-servers: $kafkaInternalBootstrapServers
                        security-protocol: PLAINTEXT
                    auth:
                        m2m:
                          url: ${internalOidcIssuer()}
                          clientId: test
                          clientSecret: test
                    """.trimIndent()
                )
            }
        }
    }
}
