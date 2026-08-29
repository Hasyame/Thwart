package com.hasyame.marvelchampions.domain.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScenarioRandomizerTest {

    private val pools = RandomizerPools(
        scenarios = listOf(
            SetRef("rhino", "core"),
            SetRef("klaw", "core"),
            SetRef("ultron", "core"),
        ),
        modularSets = listOf(
            SetRef("bomb_scare", "core"),
            SetRef("masters_of_evil", "core"),
            SetRef("under_attack", "core"),
            SetRef("legions_of_hydra", "core"),
        ),
        heroes = listOf(
            HeroRef("spider_man", "core"),
            HeroRef("she_hulk", "core"),
            HeroRef("deadpool", "deadpool"),
            HeroRef("magneto", "magneto"),
        ),
        aspects = listOf("aggression", "justice", "leadership", "protection", "pool"),
    )

    private val rules = mapOf(
        "rhino" to ScenarioRule("rhino", "core", modularCount = 1),
        "klaw" to ScenarioRule("klaw", "core", modularCount = 1),
        "ultron" to ScenarioRule(
            code = "ultron",
            packCode = "core",
            modularCount = 2,
            mandatoryModulars = listOf("under_attack"),
        ),
    )

    private fun draw(
        filters: RandomizerFilters = RandomizerFilters(),
        previous: RandomizerDraw = RandomizerDraw(),
        locked: Set<DrawField> = emptySet(),
        seed: Int = 42,
        pools: RandomizerPools = this.pools,
    ) = ScenarioRandomizer.draw(
        pools = pools,
        rules = rules,
        filters = filters,
        previous = previous,
        locked = locked,
        random = Random(seed),
    )

    @Test
    fun `extra difficulty sets are never drawn unless asked for`() {
        // The default matters more than the feature: a table that never touches
        // the switch must keep getting the difficulty the box describes.
        repeat(50) { seed ->
            assertTrue(draw(seed = seed).extraDifficulties.isEmpty())
        }
    }

    @Test
    fun `extra difficulty sets are owned, allowed, and not the one already drawn`() {
        val owned = RandomizerPools(
            scenarios = pools.scenarios,
            modularSets = pools.modularSets,
            heroes = pools.heroes,
            aspects = pools.aspects,
            difficulties = listOf(
                Difficulty.STANDARD_I,
                Difficulty.EXPERT_I,
                Difficulty.STANDARD_II,
            ),
        )
        repeat(50) { seed ->
            val result = draw(
                filters = RandomizerFilters(includeExtraDifficulty = true),
                pools = owned,
                seed = seed,
            )
            result.extraDifficulties.forEach { extra ->
                assertTrue("drew an unowned set", extra in owned.difficulties)
                // Shuffling in the set already in the deck is not a thing you
                // can do at a table.
                assertTrue("drew the main difficulty again", extra != result.difficulty)
            }
            assertEquals(
                "the same set twice",
                result.extraDifficulties.size,
                result.extraDifficulties.toSet().size,
            )
        }
    }

    @Test
    fun `the switch can actually produce an extra set`() {
        // Guards the opposite mistake from the default test: a switch that is
        // wired up but never yields anything would pass everything above.
        val owned = RandomizerPools(
            scenarios = pools.scenarios,
            modularSets = pools.modularSets,
            heroes = pools.heroes,
            aspects = pools.aspects,
            difficulties = Difficulty.entries,
        )
        val everProduced = (0..50).any { seed ->
            draw(
                filters = RandomizerFilters(includeExtraDifficulty = true),
                pools = owned,
                seed = seed,
            ).extraDifficulties.isNotEmpty()
        }
        assertTrue("the switch never gave an extra set in 51 draws", everProduced)
    }

    @Test
    fun `a draw only uses scenarios from the pool`() {
        repeat(50) { seed ->
            val result = draw(seed = seed)
            assertTrue(result.scenarioCode in pools.scenarios.map { it.code })
        }
    }

    @Test
    fun `the same seed produces the same draw`() {
        assertEquals(draw(seed = 7), draw(seed = 7))
    }

    @Test
    fun `mandatory modular sets are always included`() {
        repeat(50) { seed ->
            val result = draw(previous = RandomizerDraw(scenarioCode = "ultron"), locked = setOf(DrawField.SCENARIO), seed = seed)
            assertTrue(
                "ultron must always bring Under Attack, got ${result.modularSetCodes}",
                "under_attack" in result.modularSetCodes,
            )
        }
    }

    @Test
    fun `mandatory sets count towards the scenario total rather than adding to it`() {
        repeat(50) { seed ->
            val result = draw(
                previous = RandomizerDraw(scenarioCode = "ultron"),
                locked = setOf(DrawField.SCENARIO),
                seed = seed,
            )
            // ultron asks for 2 modular sets, one of which is mandatory.
            assertEquals(2, result.modularSetCodes.size)
        }
    }

    @Test
    fun `modular sets are never duplicated`() {
        repeat(50) { seed ->
            val result = draw(seed = seed)
            assertEquals(
                result.modularSetCodes.size,
                result.modularSetCodes.distinct().size,
            )
        }
    }

    @Test
    fun `a mandatory set from an unowned pack is dropped rather than forced`() {
        val poolsWithoutUnderAttack = pools.copy(
            modularSets = pools.modularSets.filter { it.code != "under_attack" },
        )
        val result = ScenarioRandomizer.draw(
            pools = poolsWithoutUnderAttack,
            rules = rules,
            previous = RandomizerDraw(scenarioCode = "ultron"),
            locked = setOf(DrawField.SCENARIO),
            random = Random(1),
        )

        assertFalse("under_attack" in result.modularSetCodes)
        assertTrue(result.mandatoryModularCodes.isEmpty())
    }

    @Test
    fun `pool aspect is only ever given to deadpool`() {
        repeat(200) { seed ->
            val result = draw(seed = seed)
            result.heroes.forEach { assignment ->
                if (assignment.aspect == ScenarioRandomizer.POOL_ASPECT) {
                    assertEquals(
                        "'Pool cards are Deadpool-only; assigning them elsewhere is an illegal deck",
                        "deadpool",
                        assignment.heroCode,
                    )
                }
            }
        }
    }

    @Test
    fun `a locked scenario survives a reroll of everything else`() {
        val first = draw(seed = 3)
        val second = draw(previous = first, locked = setOf(DrawField.SCENARIO), seed = 99)

        assertEquals(first.scenarioCode, second.scenarioCode)
    }

    @Test
    fun `locked heroes and aspects both survive`() {
        val first = draw(seed = 3)
        val second = draw(
            previous = first,
            locked = setOf(DrawField.HEROES, DrawField.ASPECTS, DrawField.PLAYER_COUNT),
            seed = 99,
        )

        assertEquals(first.heroes, second.heroes)
    }

    @Test
    fun `excluded scenarios are never drawn`() {
        val filters = RandomizerFilters(excludedScenarios = setOf("rhino", "klaw"))
        repeat(50) { seed ->
            assertEquals("ultron", draw(filters = filters, seed = seed).scenarioCode)
        }
    }

    @Test
    fun `excluded heroes are never drawn`() {
        val filters = RandomizerFilters(
            excludedHeroes = setOf("spider_man", "she_hulk", "magneto"),
        )
        repeat(50) { seed ->
            val result = draw(filters = filters, seed = seed)
            assertTrue(result.heroes.all { it.heroCode == "deadpool" })
        }
    }

    @Test
    fun `excluded aspects are never drawn`() {
        val filters = RandomizerFilters(
            excludedAspects = setOf("aggression", "justice", "pool", "protection"),
        )
        repeat(50) { seed ->
            val result = draw(filters = filters, seed = seed)
            assertTrue(result.heroes.all { it.aspect == "leadership" })
        }
    }

    @Test
    fun `player count stays inside the requested range`() {
        val filters = RandomizerFilters(minPlayers = 2, maxPlayers = 3)
        repeat(100) { seed ->
            val count = draw(filters = filters, seed = seed).playerCount
            assertTrue("got $count", count in 2..3)
        }
    }

    @Test
    fun `one hero is drawn per player and none repeats`() {
        val filters = RandomizerFilters(minPlayers = 3, maxPlayers = 3)
        repeat(50) { seed ->
            val result = draw(filters = filters, seed = seed)
            assertEquals(3, result.heroes.size)
            assertEquals(3, result.heroes.map { it.heroCode }.distinct().size)
        }
    }

    @Test
    fun `only allowed difficulties are drawn`() {
        val filters = RandomizerFilters(allowedDifficulties = setOf(Difficulty.EXPERT_II))
        repeat(50) { seed ->
            assertEquals(Difficulty.EXPERT_II, draw(filters = filters, seed = seed).difficulty)
        }
    }

    @Test
    fun `an empty collection yields an incomplete draw rather than crashing`() {
        val result = ScenarioRandomizer.draw(
            pools = RandomizerPools(),
            rules = rules,
            random = Random(1),
        )

        assertNull(result.scenarioCode)
        assertTrue(result.heroes.isEmpty())
        assertFalse(result.isComplete)
    }

    @Test
    fun `fewer heroes than players yields what is available rather than repeating`() {
        val onlyTwoHeroes = pools.copy(heroes = pools.heroes.take(2))
        val result = ScenarioRandomizer.draw(
            pools = onlyTwoHeroes,
            rules = rules,
            filters = RandomizerFilters(minPlayers = 4, maxPlayers = 4),
            random = Random(5),
        )

        assertEquals(2, result.heroes.size)
        assertEquals(4, result.playerCount)
    }

    @Test
    fun `a scenario with no rule entry draws no modular sets`() {
        val result = ScenarioRandomizer.draw(
            pools = pools,
            rules = emptyMap(),
            random = Random(1),
        )

        assertNotNull(result.scenarioCode)
        assertTrue(result.modularSetCodes.isEmpty())
    }

    @Test
    fun `rerolling a single field leaves every other field untouched`() {
        val first = draw(seed = 11)
        val second = ScenarioRandomizer.draw(
            pools = pools,
            rules = rules,
            previous = first,
            locked = DrawField.entries.toSet() - DrawField.DIFFICULTY,
            random = Random(12),
        )

        assertEquals(first.scenarioCode, second.scenarioCode)
        assertEquals(first.heroes, second.heroes)
        assertEquals(first.playerCount, second.playerCount)
        assertEquals(first.modularSetCodes, second.modularSetCodes)
    }
}
