package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every `{card:CODE}` in a campaign must name a card that actually exists.
 *
 * A placeholder that resolves to nothing does not fail loudly: the player is
 * shown the raw code in the middle of a French sentence, and the only way to
 * notice is to reach that step in that scenario. It has happened once already,
 * when Gene Pool was written as a guessed code rather than the real one.
 *
 * Fear No Evil is the one campaign allowed codes of its own, because its
 * encounter cards are not published anywhere; it declares those names in
 * `localCardNames`, and the same resolution path reads them, so they count as
 * resolvable here too.
 */
@RunWith(RobolectricTestRunner::class)
class CardPlaceholdersResolveTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Mirrors CampaignRepository's own placeholder pattern. */
    private val placeholder = Regex("""\{card:([A-Za-z0-9_]+)\}""")

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun asset(path: String): String =
        context().assets.open(path).bufferedReader().use { it.readText() }

    /**
     * True when the card seed has been fetched into assets.
     *
     * `assets/seed` is gitignored and downloaded by `./gradlew fetchCardSeed`,
     * so it is present on a development machine and absent on a clean runner.
     */
    private fun seedPresent(): Boolean =
        context().assets.list("seed").orEmpty().any { it.endsWith(".json") }

    /** Card codes the app ships, from the seed the database is built from. */
    private fun seededCodes(file: String): Set<String> {
        val root = json.parseToJsonElement(asset("seed/$file"))
        val cards = when (root) {
            is JsonArray -> root
            is JsonObject -> root["cards"] as? JsonArray ?: JsonArray(root.values.toList())
            else -> JsonArray(emptyList())
        }
        return cards.mapNotNull { element ->
            (element as? JsonObject)?.get("code")?.let { (it as? JsonPrimitive)?.content }
        }.toSet()
    }

    /** Every string anywhere in the template, so no corner is missed. */
    private fun strings(element: JsonElement, into: MutableList<String>) {
        when (element) {
            is JsonObject -> element.values.forEach { strings(it, into) }
            is JsonArray -> element.forEach { strings(it, into) }
            is JsonPrimitive -> if (element.isString) into.add(element.content)
        }
    }

    @Test
    fun `every card placeholder names a card the app can find`() {
        // Skipped, not failed, when the seed has not been fetched: without it
        // there is nothing to check the codes against, and a test that cannot
        // run is not the same as a test that found a problem.
        assumeTrue("card seed not fetched, run ./gradlew fetchCardSeed", seedPresent())

        val known = seededCodes("cards_fr.json") + seededCodes("cards_en.json")
        assertTrue("no seed cards were loaded, the test would pass vacuously", known.size > 1000)

        val unresolved = mutableListOf<String>()
        context().assets.list("campaigns").orEmpty().filter { it.endsWith(".json") }
            .forEach { name ->
                val text = asset("campaigns/$name")
                val local = json.decodeFromString(CampaignTemplate.serializer(), text)
                    .localCardNames.keys

                val found = mutableListOf<String>()
                strings(json.parseToJsonElement(text).jsonObject, found)
                found.flatMap { placeholder.findAll(it).map { m -> m.groupValues[1] } }
                    .distinct()
                    .filter { it !in known && it !in local }
                    .forEach { unresolved.add("$name -> {card:$it}") }
            }

        assertTrue(
            "these placeholders resolve to nothing and would print as raw codes: " +
                unresolved.joinToString(", "),
            unresolved.isEmpty(),
        )
    }

    @Test
    fun `every locally named card has a French name`() {
        // These names exist because the pack has no MarvelCDB entry, and they
        // were read off the French box, so French is the half that must be
        // there. The English half is optional on purpose: LocalizedText falls
        // back, and leaving it out is how the campaign page knows to report
        // that the English is unfinished rather than silently showing French
        // and calling it translated.
        val missing = mutableListOf<String>()
        context().assets.list("campaigns").orEmpty().filter { it.endsWith(".json") }
            .forEach { name ->
                val template = json.decodeFromString(
                    CampaignTemplate.serializer(),
                    asset("campaigns/$name"),
                )
                template.localCardNames.forEach { (code, text) ->
                    if (text.fr.isNullOrBlank()) {
                        missing.add("$name -> $code")
                    }
                }
            }
        assertTrue("local card names with no French: $missing", missing.isEmpty())
    }
}
