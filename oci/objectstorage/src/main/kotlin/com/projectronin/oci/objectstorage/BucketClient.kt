package com.projectronin.oci.objectstorage

import com.oracle.bmc.objectstorage.model.Bucket
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.model.CreateReplicationPolicyDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.CreateReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.DeleteReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.ListReplicationPoliciesRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import mu.KLogger
import mu.KotlinLogging
import java.security.MessageDigest
import java.util.Base64

class BucketClient(
    private val clients: ObjectStorageClients,
    private val namespace: String,
    private val compartment: String
) {
    private val logger: KLogger = KotlinLogging.logger { }

    fun createBucket(bucketName: String): Result<Unit> {
        return runCatching {
            clients.primary.createBucket(
                CreateBucketRequest.builder()
                    .namespaceName(namespace)
                    .createBucketDetails(
                        CreateBucketDetails.builder()
                            .name(bucketName)
                            .compartmentId(compartment)
                            .autoTiering(Bucket.AutoTiering.Disabled)
                            .publicAccessType(CreateBucketDetails.PublicAccessType.NoPublicAccess)
                            .storageTier(CreateBucketDetails.StorageTier.Standard)
                            .objectEventsEnabled(false)
                            .build()
                    )
                    .build()
            )
            logger.info("Bucket $bucketName created in primary region ${clients.primary.region}.")

            clients.secondary?.let { secondary ->
                val replicaBucket = "$bucketName-copy"
                secondary.createBucket(
                    CreateBucketRequest.builder()
                        .namespaceName(namespace)
                        .createBucketDetails(
                            CreateBucketDetails.builder()
                                .name(replicaBucket)
                                .compartmentId(compartment)
                                .autoTiering(Bucket.AutoTiering.InfrequentAccess)
                                .publicAccessType(CreateBucketDetails.PublicAccessType.NoPublicAccess)
                                .storageTier(CreateBucketDetails.StorageTier.Standard)
                                .objectEventsEnabled(false)
                                .build()
                        )
                        .build()
                )
                logger.info("Bucket $replicaBucket created in secondary region ${secondary.region}.")

                clients.primary.createReplicationPolicy(
                    CreateReplicationPolicyRequest.builder()
                        .bucketName(bucketName)
                        .namespaceName(namespace)
                        .createReplicationPolicyDetails(
                            CreateReplicationPolicyDetails.builder()
                                .name("asset backup policy")
                                .destinationBucketName(replicaBucket)
                                .destinationRegionName(secondary.region)
                                .build()
                        )
                        .build()
                )
                logger.info("Bucket replication created from $bucketName to $replicaBucket")
            }
        }
    }

    fun deleteBucket(bucketName: String): Result<Unit> {
        return runCatching {
            clients.secondary?.let { secondary ->
                val replicaBucket = "$bucketName-copy"

                // Retrieve the current replication policies
                val policies = clients.primary.listReplicationPolicies(
                    ListReplicationPoliciesRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(bucketName)
                        .build()
                )

                val policy = policies.items.find { it.destinationBucketName == replicaBucket }
                    ?: throw OciReplicationException(
                        "Delete request on bucket $bucketName but failed to find existing " +
                            "replication policy for replica bucket $replicaBucket."
                    )

                // First delete the replication to make the backup bucket writeable.
                clients.primary.deleteReplicationPolicy(
                    DeleteReplicationPolicyRequest.builder()
                        .bucketName(bucketName)
                        .namespaceName(namespace)
                        .replicationId(policy.id)
                        .build()
                )

                // After replication policy is deleted, delete the replica bucket
                secondary.deleteBucket(
                    DeleteBucketRequest.builder()
                        .bucketName(replicaBucket)
                        .namespaceName(namespace)
                        .build()
                )
            }

            // Finally delete the primary bucket
            clients.primary.deleteBucket(
                DeleteBucketRequest.builder()
                    .bucketName(bucketName)
                    .namespaceName(namespace)
                    .build()
            )
        }
    }

    fun writeObject(bucketName: String, objectName: String, content: ByteArray): Result<Unit> {
        return runCatching {
            clients.primary.putObject(
                PutObjectRequest.builder()
                    .bucketName(bucketName)
                    .namespaceName(namespace)
                    .objectName(objectName)
                    .contentMD5(content.md5)
                    .putObjectBody(content.inputStream())
                    .build()
            )
        }
    }

    fun getObject(bucketName: String, objectName: String): Result<ByteArray> {
        return runCatching {
            val objectResponse = clients.primary.getObject(
                GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build()
            )

            val objectContent = objectResponse.inputStream.use { it.readAllBytes() }
            // Check MD5 in response to calculated of the content
            check(objectContent.md5Equals(objectResponse.contentMd5)) {
                "Downloaded file from Object Store checksum didn't match"
            }

            return@runCatching objectContent
        }
    }

    fun deleteObject(bucketName: String, objectName: String): Result<Unit> {
        return runCatching {
            clients.primary.deleteObject(
                DeleteObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build()
            )
        }
    }
}

fun ByteArray.md5Equals(md5: String): Boolean {
    return md5 == this.md5
}

val ByteArray.md5: String
    get() {
        val md = MessageDigest.getInstance("MD5")
        return Base64.getEncoder().encodeToString(md.digest(this))
    }
