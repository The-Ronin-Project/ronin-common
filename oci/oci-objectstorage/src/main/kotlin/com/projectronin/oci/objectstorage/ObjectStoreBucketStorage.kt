package com.projectronin.oci.objectstorage

import com.projectronin.bucketstorage.BucketStorage

class ObjectStoreBucketStorage(
    private val bucketClient: BucketClient
) : BucketStorage {

    override fun write(bucketName: String, objectName: String, content: ByteArray): Result<Unit> {
        return bucketClient.writeObject(bucketName, objectName, content)
    }

    override fun read(bucketName: String, objectName: String): Result<ByteArray> {
        return bucketClient.readObject(bucketName, objectName)
    }

    override fun delete(bucketName: String, objectName: String): Result<Unit> {
        return bucketClient.deleteObject(bucketName, objectName)
    }
}
