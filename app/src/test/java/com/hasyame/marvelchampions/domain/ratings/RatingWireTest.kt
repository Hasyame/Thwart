package com.hasyame.marvelchampions.domain.ratings

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shape a rating takes on the wire, which both clients and the server
 * read, and the keys that name what is rated. Spelled by the contract,
 * `docs/spec/ratings-and-modular-sets.md` sections 2.2 and 2.3; a client that
 * drifts from either makes the averages meaningless.
 */
class RatingWireTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the keys read as the contract spells them`() {
        assertEquals("scenario:rhino", RatingSubject.scenario("rhino").key)
        assertEquals("modular:bomb_scare@rhino", RatingSubject.modular("bomb_scare", "rhino").key)
        assertEquals("campaign:gmw", RatingSubject.campaign("gmw").key)
        assertEquals("modular:bomb_scare", RatingSubject.modularOverallKey("bomb_scare"))
    }

    @Test
    fun `a game is rated on its scenario, then each set paired with it`() {
        val play = play(modularSets = "bomb_scare,masters_of_evil")
        assertEquals(
            listOf("scenario:rhino", "modular:bomb_scare@rhino", "modular:masters_of_evil@rhino"),
            RatingSubject.ofPlay(play).map { it.key },
        )
        // A campaign scenario, resolved to no set, is nothing to rate.
        assertEquals(emptyList<RatingSubject>(), RatingSubject.ofPlay(play, scenarioSetCode = null))
    }

    @Test
    fun `the body nests the evidence and the context, and carries nothing of the row`() {
        val wire = RatingWire(
            subject = "modular:bomb_scare@rhino",
            score = 3,
            ratedAt = 1_789_100_000_000L,
            evidence = RatingWire.Evidence(playId = "p1"),
            context = RatingWire.Context(
                players = 2,
                heroes = listOf(RatingWire.Hero("spiderman", "justice"), RatingWire.Hero("captain_america", "leadership, protection")),
                mode = "expert_i",
                standardSet = "standard_ii",
                scenario = "rhino",
            ),
        )
        val body = json.encodeToJsonElement(RatingWire.serializer(), wire).jsonObject

        assertEquals(setOf("subject", "score", "ratedAt", "evidence", "context"), body.keys)
        assertEquals("p1", body["evidence"]!!.jsonObject["playId"]!!.jsonPrimitive.content)
        assertNull(body["evidence"]!!.jsonObject["runId"])
        assertEquals("rhino", body["context"]!!.jsonObject["scenario"]!!.jsonPrimitive.content)
        assertEquals(2, body["context"]!!.jsonObject["heroes"]!!.let { (it as? JsonObject)?.size ?: json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(RatingWire.Hero.serializer()), it).size })

        // And it comes back the same through the row.
        assertEquals(wire, RatingWire.of(wire.toEntity()))
    }

    @Test
    fun `a body without a context reads back without one`() {
        // Absent keys mean absent, never zero: an old rating is not one from a
        // game nobody was at.
        val old = json.decodeFromString(RatingWire.serializer(), """{"subject":"scenario:rhino","score":4,"ratedAt":5,"evidence":{"playId":"p1"}}""")
        assertNull(old.context)
        assertNull(RatingWire.of(old.toEntity()).context)
    }

    private fun play(modularSets: String) = PlayEntity(
        id = "p1",
        playedAt = 0,
        scenarioCode = "rhino",
        scenarioName = "Rhino",
        difficulty = "standard_i",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        roster = listOf(PlayHero("01001a", "Spider-Man", "justice")),
        won = true,
        modularSets = modularSets,
    )
}
