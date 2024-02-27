package com.projectronin.bucketstorage.filesystem

import com.projectronin.bucketstorage.BucketStorage
import com.projectronin.bucketstorage.exceptions.FileNotDeletedException
import java.io.File
import java.io.FileNotFoundException

class LocalFileBucketStorage(
    private val rootDirectory: String
) : BucketStorage {

    override fun write(bucketName: String, objectName: String, content: ByteArray): Result<Unit> = runCatching {
        val path = pathFrom(bucketName, objectName)
        val dirs = objectName.substringBefore("/")

        File("$rootDirectory/$bucketName/$dirs").mkdirs()
        File(path).writeBytes(content)
    }

    override fun read(bucketName: String, objectName: String): Result<ByteArray> = runCatching {
        val path = pathFrom(bucketName, objectName)
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException(path)
        }
        file.readBytes()
    }

    override fun delete(bucketName: String, objectName: String): Result<Unit> {
        val path = pathFrom(bucketName, objectName)
        val file = File(path)

        if (!file.exists()) {
            return Result.failure(FileNotDeletedException(path))
        }

        return runCatching { file.delete() }
            .mapCatching { }
            .recoverCatching { throw FileNotDeletedException(path) }
    }

    private fun pathFrom(bucketName: String, objectName: String): String {
        return "${rootDirectory}$bucketName/$objectName"
    }
}
