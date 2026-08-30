package com.hasyame.marvelchampions

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Apostrophes in string resources must be escaped.
 *
 * An unescaped one is a build failure, and the message aapt gives for it is
 * "Invalid unicode escape sequence in string" — which names neither the
 * apostrophe nor, reliably, the right line. French is full of apostrophes, so
 * this has cost more time than any other single mistake in the project.
 *
 * Catching it here turns a cryptic resource-merge failure into a test naming
 * the exact string.
 */
class StringResourceEscapingTest {

    private val stringFiles: List<File>
        get() = File("src/main/res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .mapNotNull { File(it, "strings.xml").takeIf(File::exists) }

    @Test
    fun `every string resource escapes its apostrophes`() {
        val pattern = Regex("""<string name="([^"]+)">(.*)</string>""")
        val offenders = mutableListOf<String>()

        stringFiles.forEach { file ->
            file.readLines().forEach { line ->
                val match = pattern.find(line) ?: return@forEach
                val (name, body) = match.destructured
                // Every apostrophe must be preceded by a backslash. Walking the
                // string is clearer here than a lookbehind, and handles the
                // already-escaped backslash case without a second pattern.
                body.forEachIndexed { index, char ->
                    if (char == '\'' && (index == 0 || body[index - 1] != '\\')) {
                        offenders += "${file.parentFile?.name}/$name"
                    }
                }
            }
        }

        assertTrue(
            "unescaped apostrophe in: ${offenders.distinct()}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the check is actually looking at some files`() {
        // A wrong working directory would make the test above pass by finding
        // nothing at all, which is the worst way for a guard to fail.
        assertTrue("no strings.xml found — is the working directory right?", stringFiles.size >= 2)
    }
}
