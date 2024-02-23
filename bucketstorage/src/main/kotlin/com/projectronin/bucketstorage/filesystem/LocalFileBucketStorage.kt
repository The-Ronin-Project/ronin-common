package com.projectronin.bucketstorage.filesystem

import com.projectronin.bucketstorage.BucketStorage
import com.projectronin.bucketstorage.exceptions.FileNotDeletedException
import mu.KLogger
import mu.KotlinLogging
import java.io.File
import java.io.FileNotFoundException

class LocalFileBucketStorage(
    private val rootDirectory: String
) : BucketStorage {
    private val logger: KLogger = KotlinLogging.logger { }

    override fun write(bucketName: String, objectName: String, content: ByteArray): Result<Unit> = runCatching {
        val path = getFilePath(bucketName, objectName)
        val dirs = objectName.substringBefore("/")

        File("$rootDirectory/$bucketName/$dirs").mkdirs()
        File(path).writeBytes(content)
    }

    override fun read(bucketName: String, objectName: String): Result<ByteArray> = runCatching {
        val path = getFilePath(bucketName, objectName)
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException(path)
        }
        file.readBytes()
    }

    override fun delete(bucketName: String, objectName: String): Result<Unit> {
        val path = getFilePath(bucketName, objectName)
        val file = File(path)

        return runCatching {
            if (file.exists() && !file.delete()) {
                throw FileNotDeletedException(path)
            }
        }.onFailure {
            logger.info("File not deleted for location: $path. ${it.message}")
        }
    }

    private fun getFilePath(bucketName: String, objectName: String): String {
        return "${rootDirectory}$bucketName/$objectName"
    }
}
