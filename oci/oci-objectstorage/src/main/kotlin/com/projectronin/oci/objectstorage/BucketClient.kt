package com.projectronin.oci.objectstorage

import com.oracle.bmc.model.BmcException
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
import com.projectronin.bucketstorage.Md5
import com.projectronin.bucketstorage.md5
import mu.KLogger
import mu.KotlinLogging
import java.time.Instant

class BucketClient(
    val namespace: String,
    val compartment: String,
    val primary: OciObjectStorageClient,
    val secondary: OciObjectStorageClient? = null
) {
    private val logger: KLogger = KotlinLogging.logger { }

    var lastHealthCheck: Instant = Instant.now()
    var lastHealthException: BmcException? = null

    fun createBucket(bucketName: String): Result<Unit> {
        return runCatching {
            primary.createBucket(
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

            logger.info("Bucket $bucketName created in primary region ${primary.region}.")

            secondary?.let { secondary ->
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

                primary.createReplicationPolicy(
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

    fun deleteBucket(bucketName: String): Result<Unit> = runCatching {
        secondary?.let { secondary ->
            val replicaBucket = "$bucketName-copy"

            // Retrieve the current replication policies
            val policies = primary.listReplicationPolicies(
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
            primary.deleteReplicationPolicy(
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
        primary.deleteBucket(
            DeleteBucketRequest.builder()
                .bucketName(bucketName)
                .namespaceName(namespace)
                .build()
        )
    }

    fun readObject(bucketName: String, objectName: String): Result<ByteArray> = runCatching {
        val objectResponse = primary.getObject(
            GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .build()
        )

        val objectContent = objectResponse.inputStream.use { it.readAllBytes() }
        // Check MD5 in response to calculated of the content
        check(objectContent.md5() == Md5(objectResponse.contentMd5)) {
            "Downloaded file from Object Store checksum didn't match"
        }

        return@runCatching objectContent
    }

    fun writeObject(bucketName: String, objectName: String, content: ByteArray): Result<Unit> = runCatching {
        primary.putObject(
            PutObjectRequest.builder()
                .bucketName(bucketName)
                .namespaceName(namespace)
                .objectName(objectName)
                .contentMD5(content.md5().toString())
                .putObjectBody(content.inputStream())
                .build()
        )
    }

    fun deleteObject(bucketName: String, objectName: String): Result<Unit> = runCatching {
        primary.deleteObject(
            DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .build()
        )
    }
}
