package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.repository.AchievementRepository
import com.hasyame.marvelchampions.domain.achievements.AchievementDefinitions
import com.hasyame.marvelchampions.domain.achievements.DifficultyLevel
import com.hasyame.marvelchampions.ui.achievements.AchievementTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

/**
 * The bundled `achievements.json` is a snapshot of the web repository's
 * `web/public/achievements.json`, the master copy, taken at release: a
 * release artifact, not a live link (docs/spec/achievements/sync.md §4).
 *
 * Two guards. The snapshot's hash is pinned here, with the web commit it
 * was copied at, so a stray edit on this side fails the build; and when
 * the web checkout is beside this one, the two files are compared byte
 * for byte, so a definitions change on the web that has not been copied
 * yet is caught before the tag.
 */
@RunWith(RobolectricTestRunner::class)
class AchievementsAssetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val bundled: ByteArray =
        context.assets.open(AchievementRepository.DEFINITIONS_ASSET).use { it.readBytes() }

    @Test
    fun `the snapshot is the one copied from the web repository`() {
        assertEquals(WEB_SHA256, sha256(bundled))
    }

    @Test
    fun `the snapshot matches the web checkout beside this one, when there is one`() {
        val web = File(System.getenv("THWART_WEB_DIR") ?: WEB_CHECKOUT, "web/public/achievements.json")
        assumeTrue("no web checkout at ${web.path}", web.isFile)
        assertEquals("copy web/public/achievements.json into app/src/main/assets", sha256(web.readBytes()), sha256(bundled))
    }

    @Test
    fun `the snapshot loads, at the schema and scale this build implements`() {
        val file = AchievementDefinitions.parse(bundled.decodeToString())
        assertEquals(AchievementDefinitions.SCHEMA_VERSION, file.schemaVersion)
        assertEquals(DifficultyLevel.SCALE_VERSION, file.difficultyScaleVersion)
        assertEquals(2, file.definitionsVersion)
        assertEquals(43, file.achievements.size)
    }

    @Test
    fun `every achievement has its words in both languages`() {
        val file = AchievementDefinitions.parse(bundled.decodeToString())
        file.achievements.forEach { definition ->
            assertNotNull("title of ${definition.id}", AchievementTexts.title(definition.id))
            assertNotNull(
                "description of ${definition.id}",
                AchievementTexts.description(definition.id) ?: AchievementTexts.countDescription(definition.id),
            )
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `vectors are pinned to the all seat contract and match the web when available`() {
        val vectors = javaClass.getResource("/achievements/test-vectors.json")!!.readBytes()
        assertEquals("1c08289539f7c67f8a40d9b539f7afa1709a5e67938b101c8156c764cf376ba9", sha256(vectors))
        val web = File(System.getenv("THWART_WEB_DIR") ?: WEB_CHECKOUT, "docs/spec/achievements/test-vectors.json")
        if (web.isFile) assertEquals("Shared vectors drifted", sha256(web.readBytes()), sha256(vectors))
    }

    private companion object {
        /** `web/public/achievements.json` at Web 8148f9931c49175886d637741fa277446ac8a7d8 (2026-09-20). */
        const val WEB_SHA256 = "aa7e18db71076a5448340d3fb95a55615f41a94f506207dbbc26f241042770fb"
        const val WEB_CHECKOUT = "C:/Thwart Web"
    }
}
