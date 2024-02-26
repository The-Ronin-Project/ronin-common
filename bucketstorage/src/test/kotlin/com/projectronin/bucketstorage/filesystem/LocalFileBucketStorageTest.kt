package com.projectronin.bucketstorage.filesystem

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Base64

class LocalFileBucketStorageTest {
    private val redDot = "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12" +
        "P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
    private val redDotBytes = Base64.getDecoder().decode(redDot)
    private val localFileDirectory = "src/test/resources/"

    @Test
    fun `test read and write of file`() {
        val service = LocalFileBucketStorage(localFileDirectory)
        service.write("testBucket", "testPatient/dot", redDotBytes)

        val writtenFile = File("src/test/resources/testBucket/testPatient/dot")
        assertThat(writtenFile.isFile).isTrue()

        val readFile = service.read("testBucket", "testPatient/dot")
        assertThat(readFile.getOrNull()).isEqualTo(redDotBytes)
        writtenFile.delete()
    }

    @Test
    fun `test file deletion`() {
        val service = LocalFileBucketStorage(localFileDirectory)
        service.write("testBucket", "testPatient/dot", redDotBytes)

        val writtenFile = File("src/test/resources/testBucket/testPatient/dot")
        assertThat(writtenFile.isFile).isTrue()

        val result = service.delete("testBucket", "testPatient/dot")
        assertThat(result.isSuccess).isTrue()
        assertThat(writtenFile.exists()).isEqualTo(false)
    }
}
