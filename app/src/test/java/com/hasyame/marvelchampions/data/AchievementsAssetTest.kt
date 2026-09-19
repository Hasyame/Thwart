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
        assertEquals(1, file.definitionsVersion)
        assertEquals(31, file.achievements.size)
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

    private companion object {
        /** `web/public/achievements.json` at Thwart Web commit a99569726c3ec470fb8c39dfc9a34248fa087b10 (2026-09-18). */
        const val WEB_SHA256 = "2fac996e5c36133219fd7c2c8460b291d4b831ddbbb095259033924a2b88dfd0"
        const val WEB_CHECKOUT = "C:/Thwart Web"
    }
}
