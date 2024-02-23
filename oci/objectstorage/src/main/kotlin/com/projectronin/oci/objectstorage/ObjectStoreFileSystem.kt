package com.projectronin.oci.objectstorage

import com.projectronin.filesystem.FileSystem

class ObjectStoreFileSystem(
    private val bucketClient: BucketClient
) : FileSystem {
    override fun saveFile(bucketName: String, objectName: String, content: ByteArray): Result<Unit> {
        return bucketClient.writeObject(bucketName, objectName, content)
    }

    override fun getFile(bucketName: String, objectName: String): Result<ByteArray> {
        return bucketClient.getObject(bucketName, objectName)
    }

    override fun deleteFile(bucketName: String, objectName: String): Result<Unit> {
        return bucketClient.deleteObject(bucketName, objectName)
    }
}
