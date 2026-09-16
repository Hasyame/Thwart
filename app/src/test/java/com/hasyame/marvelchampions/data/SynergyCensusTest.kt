package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.deckbuilder.Synergy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The report the spec asks for: every player card with a "Play only if" line
 * the parser does not recognise, printed so nothing slips by, and a failure
 * if any of them mentions the identity's traits, which the parser is meant to
 * read.
 *
 * Runs over the fetched seed, so it is skipped rather than failed when the
 * seed is absent, like the other seed tests.
 */
@RunWith(RobolectricTestRunner::class)
class SynergyCensusTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val playerFactions = setOf("basic", "justice", "protection", "aggression", "leadership", "pool")

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun seedPresent(): Boolean =
        context().assets.list("seed").orEmpty().any { it.endsWith(".json") }

    private fun playerCards(): List<JsonObject> {
        val text = context().assets.open("seed/cards_en.json").bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(text)
        val cards = (root as? JsonArray) ?: root.jsonObject["cards"]?.jsonArray ?: JsonArray(emptyList())
        return cards.map { it.jsonObject }.filter {
            it["faction_code"]?.jsonPrimitive?.content in playerFactions &&
                it["type_code"]?.jsonPrimitive?.content !in setOf("hero", "alter_ego")
        }
    }

    @Test
    fun `every trait condition in the pool is recognised, and the rest is listed`() {
        assumeTrue("card seed not fetched, run ./gradlew fetchCardSeed", seedPresent())
        val cards = playerCards()
        assertTrue("the seed holds player cards", cards.size > 1000)

        val recognised = mutableListOf<String>()
        val unrecognised = mutableListOf<String>()
        cards.forEach { card ->
            val text = card["real_text"]?.jsonPrimitive?.content ?: card["text"]?.jsonPrimitive?.content
            val code = card["code"]!!.jsonPrimitive.content
            val name = card["name"]!!.jsonPrimitive.content
            Synergy.parse(text)?.let { recognised += "$code $name -> ${it.encode()}" }
            Synergy.unrecognised(text)?.let { unrecognised += "$code $name | $it" }
        }

        println("Synergy census: ${recognised.size} trait conditions recognised.")
        println("Out of scope (${unrecognised.size} cards with another kind of condition):")
        unrecognised.forEach { println("  $it") }

        // The count on 2026-09-16 was 92; a new pack adding a card in one of
        // the known forms moves it, and that is fine. A drop would mean the
        // parser lost a phrasing.
        assertTrue("recognised ${recognised.size}, expected at least 92", recognised.size >= 92)

        // None of the leftovers may be about the identity's traits: those are
        // the parser's job. Anything else stays listed for a later decision.
        val traitMentions = unrecognised.filter { line ->
            Regex("""(identity|hero|you) (has|have) the \[\[""", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }
        assertEquals("trait conditions left unrecognised", emptyList<String>(), traitMentions)
    }
}
