package com.hasyame.marvelchampions.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackMetadataFileDto
import kotlinx.serialization.builtins.ListSerializer
import com.hasyame.marvelchampions.domain.model.PackType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the curated pack table. It is hand-maintained, so a typo in a pack
 * code or type would otherwise only surface as a silently miscategorised pack
 * in the collection screen.
 */
@RunWith(RobolectricTestRunner::class)
class PackMetadataAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun readMetadata(): PackMetadataFileDto {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("pack_metadata.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(PackMetadataFileDto.serializer(), text)
    }

    /**
     * Every pack MarvelCDB publishes has been curated.
     *
     * This used to assert a hardcoded count, with a comment claiming a new
     * MarvelCDB pack would fail it. It could not: it never looked at MarvelCDB.
     * Jessica Jones and Luke Cage arrived in August 2026 and the test stayed
     * green while both showed in the app as wave 0 and Uncategorised.
     *
     * It now compares against the fetched seed, which is the same pack list the
     * app builds its database from. Skipped when the seed is absent, so a
     * developer without it is not blocked; the data workflow fetches it, which
     * is where this has to bite.
     */
    @Test
    fun `covers every pack marvelcdb currently publishes`() {
        assumeTrue("card seed not fetched, run ./gradlew fetchCardSeed", seedPresent())

        val curated = readMetadata().packs.map { it.code }.toSet()
        val published = seededPackCodes()
        val uncurated = (published - curated).sorted()

        assertTrue(
            "pack_metadata.json is missing $uncurated. Curate the type and wave " +
                "of each, or the collection screen files them under wave 0.",
            uncurated.isEmpty(),
        )
    }

    /** True when the card seed has been fetched into assets. */
    private fun seedPresent(): Boolean =
        ApplicationProvider.getApplicationContext<Context>()
            .assets.list("seed").orEmpty().any { it.endsWith(".json") }

    /** The pack codes MarvelCDB publishes, as of the last seed fetch. */
    private fun seededPackCodes(): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("seed/packs_en.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(PackDto.serializer()), text)
            .map { it.code }
            .toSet()
    }

    @Test
    fun `every pack code is unique`() {
        val codes = readMetadata().packs.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `every type is a known PackType and never UNKNOWN`() {
        readMetadata().packs.forEach { pack ->
            val type = PackType.fromName(pack.type)
            assertTrue(
                "${pack.code} has unrecognised type ${pack.type}",
                type != PackType.UNKNOWN,
            )
        }
    }

    @Test
    fun `every pack has a positive wave`() {
        readMetadata().packs.forEach { pack ->
            assertTrue("${pack.code} has wave ${pack.wave}", pack.wave >= 1)
        }
    }

    @Test
    fun `the packs the user owns are all present`() {
        val owned = listOf(
            "core",
            "msm", "magneto", "drs", "wonder_man", "hercules", "gambit", "deadpool",
            "gob", "sm",
            "fne", "aoa", "gmw", "mts",
        )
        val known = readMetadata().packs.map { it.code }.toSet()
        owned.forEach { assertTrue("$it missing from pack_metadata.json", it in known) }
    }

    @Test
    fun `exactly one core set is declared`() {
        val cores = readMetadata().packs.filter { it.type == PackType.CORE.name }
        assertEquals(listOf("core"), cores.map { it.code })
    }
}
