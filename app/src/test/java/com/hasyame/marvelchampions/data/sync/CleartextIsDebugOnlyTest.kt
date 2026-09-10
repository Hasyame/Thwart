package com.hasyame.marvelchampions.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one hole in this app's transport security stays in debug builds, and
 * stays the size it is.
 *
 * Sync needed a development server to be testable against, and Android has
 * refused cleartext by default since API 28 — rightly. The exemption that
 * unblocked it lives in `src/debug`, so it is not merged into a release at all,
 * and it names two addresses that both mean "the machine I am developing on".
 *
 * That is a security property held in place by a directory name, which is
 * exactly the kind of thing that survives until somebody moves a file. The
 * first two tests fail if the exemption ever reaches the main source set; the
 * third fails if it grows past loopback into a real host, which would make
 * every debug build willing to talk to it in the clear on a café network.
 *
 * The release APK is checked separately in CI, which is the check that actually
 * ships. This one fails in seconds on a laptop instead of ten minutes into a
 * release build.
 */
class CleartextIsDebugOnlyTest {

    @Test
    fun `the main manifest permits no cleartext of any kind`() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()

        assertFalse(
            "the release manifest must not carry a network security config",
            manifest.contains("networkSecurityConfig"),
        )
        assertFalse(
            "usesCleartextTraffic in the shipped manifest would undo all of this",
            manifest.contains("usesCleartextTraffic"),
        )
    }

    @Test
    fun `no cleartext configuration lives in the main source set`() {
        val stray = file("app/src/main/res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .filter { it.readText().contains("cleartextTrafficPermitted") }
            .map { it.name }
            .toList()

        assertEquals("this belongs in src/debug only", emptyList<String>(), stray)
    }

    @Test
    fun `the debug exemption names only this machine`() {
        val config = file("app/src/debug/res/xml/network_security_config_debug.xml").readText()
        val domains = Regex("""<domain[^>]*>([^<]+)</domain>""")
            .findAll(config)
            .map { it.groupValues[1].trim() }
            .toSet()

        assertEquals(
            "a debug build must not be willing to talk to a real host in the clear",
            setOf("10.0.2.2", "127.0.0.1", "localhost"),
            domains,
        )
        assertFalse(
            "subdomains would widen this well past the development machine",
            config.contains("includeSubdomains=\"true\""),
        )
    }

    @Test
    fun `the guard is reading real files`() {
        // A path that moved would make every assertion above vacuous.
        assertTrue(file("app/src/main/AndroidManifest.xml").isFile)
        assertTrue(file("app/src/debug/AndroidManifest.xml").isFile)
    }

    private fun file(relative: String): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (candidate in listOf(relative, relative.removePrefix("app/"))) {
                val found = File(directory, candidate)
                if (found.exists()) {
                    return found
                }
            }
            directory = directory.parentFile
        }
        error("could not find $relative from ${File("").absolutePath}")
    }
}
