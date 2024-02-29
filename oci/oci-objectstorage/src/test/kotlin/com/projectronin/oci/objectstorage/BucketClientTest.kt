package com.projectronin.oci.objectstorage

import com.oracle.bmc.objectstorage.model.ReplicationPolicySummary
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.CreateReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.DeleteReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.CreateBucketResponse
import com.oracle.bmc.objectstorage.responses.CreateReplicationPolicyResponse
import com.oracle.bmc.objectstorage.responses.DeleteBucketResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import com.oracle.bmc.objectstorage.responses.DeleteReplicationPolicyResponse
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BucketClientTest {
    private val primary = mockk<OciObjectStorageClient>()
    private val secondary = mockk<OciObjectStorageClient>()

    private val namespace = "oci_namespace"
    private val compartment = "compartment.ocid"

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @BeforeEach
    fun setUp() {
        every { primary.region }.returns("us-phoenix-1")
        every { secondary.region }.returns("us-ashburn-1")
    }

    @Test
    fun `create bucket primary only`() {
        val client = BucketClient(namespace, compartment, primary)
        val capturedEntity = slot<CreateBucketRequest>()
        every { primary.createBucket(capture(capturedEntity)) } answers {
            mockk<CreateBucketResponse>()
        }
        client.createBucket("bucketName")
        val bucketRequest = capturedEntity.captured
        assertThat(bucketRequest.namespaceName).isEqualTo(namespace)
        assertThat(bucketRequest.createBucketDetails.name).isEqualTo("bucketName")
        assertThat(bucketRequest.createBucketDetails.compartmentId).isEqualTo(compartment)

        verify(exactly = 0) { primary.createReplicationPolicy(any()) }
    }

    @Test
    fun `create bucket`() {
        val client = BucketClient(namespace, compartment, primary, secondary)
        val primaryCaptured = slot<CreateBucketRequest>()
        every { primary.createBucket(capture(primaryCaptured)) } answers {
            mockk<CreateBucketResponse>(relaxed = true)
        }
        val secondaryCaptured = slot<CreateBucketRequest>()
        every { secondary.createBucket(capture(secondaryCaptured)) } answers {
            mockk<CreateBucketResponse>()
        }
        val replicationPolicyCaptured = slot<CreateReplicationPolicyRequest>()
        every { primary.createReplicationPolicy(capture(replicationPolicyCaptured)) } answers {
            mockk<CreateReplicationPolicyResponse>()
        }

        client.createBucket("bucketName")

        val primaryBucketRequest = primaryCaptured.captured
        assertThat(primaryBucketRequest.namespaceName).isEqualTo(namespace)
        assertThat(primaryBucketRequest.createBucketDetails.name).isEqualTo("bucketName")
        assertThat(primaryBucketRequest.createBucketDetails.compartmentId).isEqualTo(compartment)

        val secondaryBucketRequest = secondaryCaptured.captured
        assertThat(secondaryBucketRequest.namespaceName).isEqualTo(namespace)
        assertThat(secondaryBucketRequest.createBucketDetails.name).isEqualTo("bucketName-copy")
        assertThat(secondaryBucketRequest.createBucketDetails.compartmentId).isEqualTo(compartment)

        val replicationRequest = replicationPolicyCaptured.captured
        assertThat(replicationRequest.bucketName).isEqualTo("bucketName")
        assertThat(replicationRequest.namespaceName).isEqualTo(namespace)
        assertThat(replicationRequest.createReplicationPolicyDetails.destinationBucketName).isEqualTo("bucketName-copy")
        assertThat(replicationRequest.createReplicationPolicyDetails.destinationRegionName).isEqualTo("us-ashburn-1")
    }

    @Test
    fun `delete bucket`() {
        val client = BucketClient(namespace, compartment, primary, secondary)
        every { primary.listReplicationPolicies(any()).items } answers {
            mutableListOf(ReplicationPolicySummary.builder().destinationBucketName("bucketName-copy").build())
        }
        val primaryCaptured = slot<DeleteBucketRequest>()
        every { primary.deleteBucket(capture(primaryCaptured)) } answers {
            mockk<DeleteBucketResponse>()
        }
        val secondaryCaptured = slot<DeleteBucketRequest>()
        every { secondary.deleteBucket(capture(secondaryCaptured)) } answers {
            mockk<DeleteBucketResponse>()
        }
        val replicationPolicyCaptured = slot<DeleteReplicationPolicyRequest>()
        every { primary.deleteReplicationPolicy(capture(replicationPolicyCaptured)) } answers {
            mockk<DeleteReplicationPolicyResponse>()
        }

        client.deleteBucket("bucketName")

        val primaryBucketRequest = primaryCaptured.captured
        assertThat(primaryBucketRequest.bucketName).isEqualTo("bucketName")
        assertThat(primaryBucketRequest.namespaceName).isEqualTo(namespace)

        val replicationPolicyRequest = replicationPolicyCaptured.captured
        assertThat(replicationPolicyRequest.bucketName).isEqualTo("bucketName")
        assertThat(replicationPolicyRequest.namespaceName).isEqualTo(namespace)

        val secondaryBucketRequest = secondaryCaptured.captured
        assertThat(secondaryBucketRequest.bucketName).isEqualTo("bucketName-copy")
        assertThat(secondaryBucketRequest.namespaceName).isEqualTo(namespace)

        verifyOrder {
            primary.listReplicationPolicies(any())
            primary.deleteReplicationPolicy(any())
            secondary.deleteBucket(any())
            primary.deleteBucket(any())
        }
    }

    @Test
    fun `delete bucket fails when bucket replication is not found`() {
        val client = BucketClient(namespace, compartment, primary, secondary)
        every { primary.listReplicationPolicies(any()).items } answers { mutableListOf() }

        val result = client.deleteBucket("bucketName")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageStartingWith("Delete request on bucket")
    }

    @Test
    fun `writeObject test`() {
        val client = BucketClient(namespace, compartment, primary, secondary)
        val captured = slot<PutObjectRequest>()
        every { primary.putObject(capture(captured)) } answers {
            mockk<PutObjectResponse>()
        }

        client.writeObject("bucketName", "objectName", "SomeContent".toByteArray())
        val putRequest = captured.captured

        assertThat(putRequest.namespaceName).isEqualTo(namespace)
        assertThat(putRequest.bucketName).isEqualTo("bucketName")
        assertThat(putRequest.objectName).isEqualTo("objectName")
    }

    @Test
    fun `getObject test`() {
        val client = BucketClient(namespace, compartment, primary, secondary)
        val getResponse = mockk<GetObjectResponse>()
        every { getResponse.contentMd5 } returns "YWg8w4wqRUGdci/gek4Xuw=="
        every { getResponse.inputStream } returns "Test String Content".byteInputStream()

        val captured = slot<GetObjectRequest>()
        every { primary.getObject(capture(captured)) } answers { getResponse }

        val bytes = client.readObject("bucketName", "objectName")
        val getRequest = captured.captured

        assertThat(getRequest.namespaceName).isEqualTo(namespace)
        assertThat(getRequest.bucketName).isEqualTo("bucketName")
        assertThat(getRequest.objectName).isEqualTo("objectName")

        assertThat(bytes.isSuccess).isEqualTo(true)
        assertThat(bytes.getOrThrow()).isEqualTo("Test String Content".toByteArray())
    }

    @Test
    fun `getObject invalid md5`() {
        val client = BucketClient(namespace, compartment, primary)
        val getResponse = mockk<GetObjectResponse>()
        every { getResponse.contentMd5 } returns "YWw4wqRUGdci/gek4Xuw=="
        every { getResponse.inputStream } returns "Test String Content".byteInputStream()

        val captured = slot<GetObjectRequest>()
        every { primary.getObject(capture(captured)) } answers { getResponse }

        val result = client.readObject("bucketName", "objectName")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageEndingWith("checksum didn't match")
    }

    @Test
    fun `deleteObject test`() {
        val client = BucketClient(namespace, compartment, primary)

        val captured = slot<DeleteObjectRequest>()
        every { primary.deleteObject(capture(captured)) } answers {
            mockk<DeleteObjectResponse>()
        }

        client.deleteObject("bucketName", "objectName")
        val deleteRequest = captured.captured

        assertThat(deleteRequest.namespaceName).isEqualTo(namespace)
        assertThat(deleteRequest.bucketName).isEqualTo("bucketName")
        assertThat(deleteRequest.objectName).isEqualTo("objectName")
    }
}
