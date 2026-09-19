package com.hasyame.marvelchampions.data.io

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedInputTest {
    @Test
    fun exactLimitAndEmptyInputAreAccepted() {
        assertArrayEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3).inputStream().readBounded(3))
        assertArrayEquals(byteArrayOf(), byteArrayOf().inputStream().readBounded(0))
    }

    @Test
    fun uncompressedAndCompressedExcessAreRejected() {
        assertTrue(runCatching { ByteArray(17).inputStream().readBounded(16) }.exceptionOrNull() is ImportTooLargeException)
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(ByteArray(1024))
            zip.closeEntry()
        }
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            zip.nextEntry
            assertTrue(runCatching { zip.readBounded(16) }.exceptionOrNull() is ImportTooLargeException)
        }
    }
}
