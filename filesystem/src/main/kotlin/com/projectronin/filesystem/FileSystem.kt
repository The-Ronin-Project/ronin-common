package com.projectronin.filesystem

interface FileSystem {
    fun saveFile(bucketName: String, objectName: String, content: ByteArray): Result<Unit>
    fun getFile(bucketName: String, objectName: String): Result<ByteArray>
    fun deleteFile(bucketName: String, objectName: String): Result<Unit>
}
