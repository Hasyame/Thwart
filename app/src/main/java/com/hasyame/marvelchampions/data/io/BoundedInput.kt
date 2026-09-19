package com.hasyame.marvelchampions.data.io

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ImportTooLargeException : IOException("Import exceeds the supported size limit")

/** Includes one sentinel byte so an exactly full document still succeeds. */
fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var remaining = limit
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, remaining + 1))
        if (count < 0) return output.toByteArray()
        if (count > remaining) throw ImportTooLargeException()
        output.write(buffer, 0, count)
        remaining -= count
    }
}
