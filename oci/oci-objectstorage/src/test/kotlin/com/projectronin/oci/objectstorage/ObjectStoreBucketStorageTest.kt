package com.projectronin.oci.objectstorage

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjectStoreBucketStorageTest {
    private val bucketClient = mockk<BucketClient>()
    val service = ObjectStoreBucketStorage(bucketClient)

    @Test
    fun `save file`() {
        every { bucketClient.writeObject("bucket", "object", "content".toByteArray()) } returns Result.success(Unit)
        service.write("bucket", "object", "content".toByteArray())
        verify(exactly = 1) { bucketClient.writeObject("bucket", "object", "content".toByteArray()) }
    }

    @Test
    fun `get file`() {
        every { bucketClient.readObject("bucket", "object") } returns Result.success("content".toByteArray())
        val result = service.read("bucket", "object")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("content".toByteArray())
    }

    @Test
    fun `delete file`() {
        every { bucketClient.deleteObject("bucket", "object") } returns Result.success(Unit)
        service.delete("bucket", "object")
        verify(exactly = 1) { bucketClient.deleteObject("bucket", "object") }
    }
}
