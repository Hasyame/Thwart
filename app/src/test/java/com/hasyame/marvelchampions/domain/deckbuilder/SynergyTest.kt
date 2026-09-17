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
 * The synergy rule against the fixture both clients share:
 * `src/test/resources/synergy-fixture.json` is the web's
 * `scripts/fixtures/synergy.json`, copied unchanged. A case that fails here
 * would fail on the web too, or the two would disagree, which is the worse
 * outcome. The file's sections are the contract's four functions: trait
 * keys, the condition a card carries, an identity's faces, compatibility.
 */
class SynergyTest {

    private val fixture: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/synergy-fixture.json")) { "fixture missing" }
            .bufferedReader().use { it.readText() },
    ).jsonObject

    private val cards: Map<String, JsonObject> = fixture["cards"]!!.jsonArray
        .map { it.jsonObject }
        .associateBy { it["code"]!!.jsonPrimitive.content }

    /** The union of every face's traits, as the contract defines an identity. */
    private val identities: Map<String, IdentityTraits> = fixture["identities"]!!.jsonArray
        .map { it.jsonObject }
        .associate { identity ->
            val faces = identity["faces"]!!.jsonArray.map { it.jsonPrimitive.content }
            identity["id"]!!.jsonPrimitive.content to IdentityTraits.of(heroTraits = faces, alterEgoTraits = emptyList())
        }

    private fun conditionOf(code: String): SynergyCondition? =
        Synergy.parse(cards.getValue(code)["real_text"]!!.jsonPrimitive.content)

    @Test
    fun `a printed traits string gives the keys the web gives`() {
        fixture["traitKeys"]!!.jsonArray.forEach { element ->
            val case = element.jsonObject
            val traits = case["traits"]!!.jsonPrimitive.content
            assertEquals(traits, case["keys"]!!.jsonArray.map { it.jsonPrimitive.content }, TraitKey.split(traits))
        }
    }

    @Test
    fun `every card parses to what the fixture expects`() {
        cards.forEach { (code, card) ->
            val name = card["name"]!!.jsonPrimitive.content
            val expected = card["synergy"]
            if (expected == null || expected is JsonNull) {
                assertNull("$code $name", conditionOf(code))
            } else {
                assertEquals(
                    "$code $name",
                    SynergyCondition(anyOfTraits = expected.jsonObject["anyOfTraits"]!!.jsonArray.map { it.jsonPrimitive.content }),
                    conditionOf(code),
                )
            }
        }
    }

    @Test
    fun `every compatibility case agrees with the fixture`() {
        fixture["compatibility"]!!.jsonArray.forEach { element ->
            val case = element.jsonObject
            val code = case["card"]!!.jsonPrimitive.content
            val identity = case["identity"]!!.jsonPrimitive.content
            val compatible = conditionOf(code)?.compatibleWith(identities.getValue(identity)) ?: true
            assertEquals("$code with $identity", case["compatible"]!!.jsonPrimitive.boolean, compatible)
        }
    }

    @Test
    fun `a condition the parser does not know is reported, one it knows is not`() {
        assertEquals(
            listOf("Play only if your hero has the [[Psionic]] trait."),
            Synergy.unrecognised("Play only if your hero has the [[Psionic]] trait.\n<b>Hero Action</b>: deal 3 damage."),
        )
        assertEquals(emptyList<String>(), Synergy.unrecognised(cards.getValue("16019")["real_text"]!!.jsonPrimitive.content))
        assertEquals(emptyList<String>(), Synergy.unrecognised(cards.getValue("01050")["real_text"]!!.jsonPrimitive.content))
        // A full stop inside a tag is not the end of the line.
        assertEquals(emptyList<String>(), Synergy.unrecognised(cards.getValue("54033")["real_text"]!!.jsonPrimitive.content))
    }

    @Test
    fun `the stored form survives the round trip and distinguishes none from underived`() {
        val both = SynergyCondition(listOf("x-force", "x-men"))
        assertEquals("|x-force|x-men|", both.encode())
        assertEquals(both, SynergyCondition.decode(both.encode()))
        // Ready for the day the contract encodes the hero-face form.
        val hero = SynergyCondition(listOf("psionic"), heroOnly = true)
        assertEquals("hero:|psionic|", hero.encode())
        assertEquals(hero, SynergyCondition.decode(hero.encode()))
        assertNull("no condition", SynergyCondition.decode(SynergyCondition.NONE))
        assertNull("not derived yet", SynergyCondition.decode(null))
    }

    @Test
    fun `a hero-only condition is answered by the hero faces alone`() {
        val psionic = SynergyCondition(listOf("psionic"), heroOnly = true)
        assertEquals(true, psionic.compatibleWith(IdentityTraits(heroFaces = setOf("psionic"), alterEgoFaces = emptySet())))
        assertEquals(false, psionic.compatibleWith(IdentityTraits(heroFaces = emptySet(), alterEgoFaces = setOf("psionic"))))
    }

    @Test
    fun `the warning names the cards the identity cannot play, and never its own`() {
        val magik = identities.getValue("magik")
        val rocket = SynergyCardInfo("16019", "Rocket Raccoon", conditionOf("16019"))
        val elixir = SynergyCardInfo("42011", "Elixir", conditionOf("42011"))
        val ownCard = SynergyCardInfo("45002", "Soulsword", SynergyCondition(listOf("guardian")), signature = true)
        assertEquals(
            listOf(SynergyWarning("16019", "Rocket Raccoon")),
            Synergy.warnings(magik, listOf(rocket, elixir, ownCard)),
        )
    }
}
