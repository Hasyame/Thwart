package com.hasyame.marvelchampions.domain.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A scenario that names its own pool, and one that scales with the table.
 *
 * MojoMania does both: its three scenarios draw only from the MojoMania pack,
 * and the headline scenario takes one modular set plus one for every player.
 * Neither was expressed before — the pack restriction was dropped and the
 * per-player set recorded as a flat one, so a four-player game was told to use
 * two sets drawn from the whole collection when it wanted five from one pack.
 */
class ScenarioOwnPoolTest {

    private val pools = RandomizerPools(
        scenarios = listOf(SetRef("mojo", "mojo"), SetRef("rhino", "core")),
        modularSets = listOf(
            SetRef("sitcom", "mojo"),
            SetRef("western", "mojo"),
            SetRef("horror", "mojo"),
            SetRef("sci-fi", "mojo"),
            SetRef("bomb", "core"),
            SetRef("goons", "core"),
        ),
        heroes = listOf(HeroRef("a", "core"), HeroRef("b", "core")),
        aspects = listOf("Justice"),
    )

    private val rules = mapOf(
        "mojo" to ScenarioRule(
            code = "mojo",
            packCode = "mojo",
            modularCount = 1,
            modularPacks = listOf("mojo"),
            modularCountPerHero = 1,
        ),
        "rhino" to ScenarioRule(code = "rhino", packCode = "core", modularCount = 1),
    )

    private fun draw(players: Int, seed: Int) = ScenarioRandomizer.draw(
        pools = pools,
        rules = rules,
        filters = RandomizerFilters(
            excludedScenarios = setOf("rhino"),
            minPlayers = players,
            maxPlayers = players,
        ),
        random = Random(seed),
    )

    @Test
    fun `a restricted scenario draws only from its own pack`() {
        val fromMojo = pools.modularSets.filter { it.packCode == "mojo" }.map { it.code }.toSet()

        repeat(40) { seed ->
            val drawn = draw(players = 2, seed = seed)
            assertTrue(
                "drew ${drawn.modularSetCodes} from outside the MojoMania pack",
                drawn.modularSetCodes.all { it in fromMojo },
            )
        }
    }

    @Test
    fun `the count grows with the table`() {
        // One set, plus one for each player: two solo, four at three players.
        repeat(20) { seed ->
            assertEquals(2, draw(players = 1, seed = seed).modularSetCodes.size)
            assertEquals(4, draw(players = 3, seed = seed).modularSetCodes.size)
        }
    }

    @Test
    fun `a restriction does not leak into other scenarios`() {
        // The MojoMania sets stay legal everywhere else. Only the versus packs
        // are illegal outside their own games, and this is not one.
        val unrestricted = ScenarioRandomizer.draw(
            pools = pools,
            rules = rules,
            filters = RandomizerFilters(excludedScenarios = setOf("mojo")),
            random = Random(7),
        )

        assertEquals("rhino", unrestricted.scenarioCode)
        assertEquals(1, unrestricted.modularSetCodes.size)
    }
}
