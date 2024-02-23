package com.projectronin.filesystem

import com.projectronin.filesystem.exceptions.FileNotDeletedException
import mu.KLogger
import mu.KotlinLogging
import java.io.File
import java.io.FileNotFoundException

class LocalFileSystemSystem(
    private val rootDirectory: String
) : FileSystem {
    private val logger: KLogger = KotlinLogging.logger { }

    override fun saveFile(bucketName: String, objectName: String, content: ByteArray): Result<Unit> {
        val path = getFilePath(bucketName, objectName)

        val dirs = objectName.substringBefore("/")
        File("$rootDirectory/$bucketName/$dirs").mkdirs()
        return runCatching {
            File(path).writeBytes(content)
        }
    }

    override fun getFile(bucketName: String, objectName: String): Result<ByteArray> {
        val path = getFilePath(bucketName, objectName)
        val file = File(path)
        if (file.exists()) {
            return Result.success(file.readBytes())
        }
        logger.info("File not found for retrieval at location: $path")
        return Result.failure(FileNotFoundException(path))
    }

    override fun deleteFile(bucketName: String, objectName: String): Result<Unit> {
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
