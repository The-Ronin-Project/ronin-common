package com.projectronin.contracttest.blueprinthack

import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration

object BlueprintJarExtractor {

    private val logger = KotlinLogging.logger { }

    @Suppress("DEPRECATION")
    fun writeBlueprintJarTo(tempDir: File): File {
        val libSubDirectory = "blueprint-libs"
        val libDir = File(tempDir, libSubDirectory)

        if (libDir.exists()) {
            libDir.deleteRecursively()
        }

        val ownerWritable = PosixFilePermissions.fromString("rwxrwxrwx")
        val permissions: FileAttribute<*> = PosixFilePermissions.asFileAttribute(ownerWritable)
        Files.createDirectory(libDir.toPath(), permissions)

        val container = GenericContainer(DockerImageName.parse("docker-repo.devops.projectronin.io/student-data-service:7ced907ae71fb263d435a38e3d3302681fae9eb1"))
            .withCreateContainerCmdModifier { cmd -> cmd.withEntrypoint("/bin/bash") }
            .withCommand("-c", "cp /app/app.jar /library-output && echo 'completed'")
            // deprecated, but large files crash everything
            .withFileSystemBind(libDir.absolutePath, "/library-output")
            .waitingFor(Wait.forLogMessage(".*completed.*", 1))
            .withStartupTimeout(Duration.ofSeconds(300))
        runCatching { container.start() }
            .onFailure { e ->
                val logs = container.logs
                logger.error { logs }
                throw RuntimeException("Failed to start: ${logs?.substring(0, 500)}", e)
            }
        container.stop()
        return libDir.resolve("app.jar")
    }
}
