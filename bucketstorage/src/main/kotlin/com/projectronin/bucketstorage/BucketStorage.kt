package com.projectronin.bucketstorage

interface BucketStorage {
    fun read(bucketName: String, objectName: String): Result<ByteArray>
    fun write(bucketName: String, objectName: String, content: ByteArray): Result<Unit>
    fun delete(bucketName: String, objectName: String): Result<Unit>
}
