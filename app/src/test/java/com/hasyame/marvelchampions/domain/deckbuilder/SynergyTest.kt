package com.hasyame.marvelchampions.domain.deckbuilder

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The synergy rule against the fixture both clients share,
 * `src/test/resources/synergy-fixture.json`. A case that fails here would
 * fail on the web too, or the two would disagree, which is the worse outcome.
 */
class SynergyTest {

    private val fixture: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/synergy-fixture.json")) { "fixture missing" }
            .bufferedReader().use { it.readText() },
    ).jsonObject

    private val identities: Map<String, IdentityTraits> = fixture["identities"]!!.jsonObject
        .mapValues { (_, faces) ->
            IdentityTraits.of(
                heroTraits = faces.jsonObject["hero"]!!.jsonArray.map { it.jsonPrimitive.content },
                alterEgoTraits = faces.jsonObject["alterEgo"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }

    private val cards: Map<String, JsonObject> = fixture["cards"]!!.jsonObject
        .mapValues { it.value.jsonObject }

    @Test
    fun `every card parses to what the fixture expects`() {
        cards.forEach { (name, card) ->
            val parsed = Synergy.parse(card["text"]!!.jsonPrimitive.content)
            val expected = card["expected"]
            if (expected == null || expected is JsonNull) {
                assertNull(name, parsed)
            } else {
                assertEquals(
                    name,
                    SynergyCondition(
                        anyOfTraits = expected.jsonObject["anyOfTraits"]!!.jsonArray.map { it.jsonPrimitive.content },
                        heroOnly = expected.jsonObject["heroOnly"]!!.jsonPrimitive.boolean,
                    ),
                    parsed,
                )
            }
        }
    }

    @Test
    fun `every case agrees with the fixture`() {
        fixture["cases"]!!.jsonArray.forEach { element ->
            val case = element.jsonObject
            val cardName = case["card"]!!.jsonPrimitive.content
            val identityName = case["identity"]!!.jsonPrimitive.content
            val condition = Synergy.parse(cards.getValue(cardName)["text"]!!.jsonPrimitive.content)
            val compatible = condition?.compatibleWith(identities.getValue(identityName)) ?: true
            assertEquals("$cardName with $identityName", case["compatible"]!!.jsonPrimitive.boolean, compatible)
        }
    }

    @Test
    fun `a condition the parser does not know is reported, one it knows is not`() {
        val spycraft = cards.getValue("spycraft_out_of_scope")
        assertEquals(spycraft["unrecognised"]!!.jsonPrimitive.content, Synergy.unrecognised(spycraft["text"]!!.jsonPrimitive.content))
        assertNull(Synergy.unrecognised(cards.getValue("rocket_raccoon")["text"]!!.jsonPrimitive.content))
        assertNull(Synergy.unrecognised(cards.getValue("helicarrier")["text"]!!.jsonPrimitive.content))
    }

    @Test
    fun `the stored form survives the round trip and distinguishes none from underived`() {
        val both = SynergyCondition(listOf("x-force", "x-men"))
        assertEquals("|x-force|x-men|", both.encode())
        assertEquals(both, SynergyCondition.decode(both.encode()))
        val hero = SynergyCondition(listOf("psionic"), heroOnly = true)
        assertEquals("hero:|psionic|", hero.encode())
        assertEquals(hero, SynergyCondition.decode(hero.encode()))
        assertNull("no condition", SynergyCondition.decode(SynergyCondition.NONE))
        assertNull("not derived yet", SynergyCondition.decode(null))
    }

    @Test
    fun `the warning names the cards the identity cannot play, and never its own`() {
        val magik = identities.getValue("magik")
        val rocket = SynergyCardInfo("16019", "Rocket Raccoon", Synergy.parse(cards.getValue("rocket_raccoon")["text"]!!.jsonPrimitive.content))
        val elixir = SynergyCardInfo("42011", "Elixir", Synergy.parse(cards.getValue("elixir")["text"]!!.jsonPrimitive.content))
        val ownCard = SynergyCardInfo("45002", "Soulsword", SynergyCondition(listOf("guardian")), signature = true)
        assertEquals(
            listOf(SynergyWarning("16019", "Rocket Raccoon")),
            Synergy.warnings(magik, listOf(rocket, elixir, ownCard)),
        )
    }
}
