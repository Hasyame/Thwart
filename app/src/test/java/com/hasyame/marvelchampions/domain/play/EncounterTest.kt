package com.hasyame.marvelchampions.domain.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counters a table keeps during a game, worked out from the printed cards.
 *
 * The numbers here are Rhino and Ultron out of the Core Set, read off the
 * cards rather than invented: Rhino is 14 health per player at stage I, 15 at
 * II, 16 at III; The Break-In! advances at 7 threat per player and accelerates
 * 1 per player per round. Getting the per-player scaling wrong is the failure
 * that matters, because a tracker showing the wrong total is worse than no
 * tracker at all.
 */
class EncounterTest {

    private val rhino = EncounterSetup(
        villain = listOf(
            EncounterSide("Rhino", "I", value = 14, perPlayer = true),
            EncounterSide("Rhino", "II", value = 15, perPlayer = true),
            EncounterSide("Rhino", "III", value = 16, perPlayer = true),
        ),
        scheme = listOf(
            EncounterSide(
                name = "The Break-In!",
                stage = "1B",
                value = 7,
                perPlayer = true,
                escalation = 1,
                escalationPerPlayer = true,
            ),
        ),
    )

    private fun rhinoFor(players: Int) = Encounter.startOf(rhino.copy(players = players))

    @Test
    fun `solo reads the printed number`() {
        val encounter = rhinoFor(players = 1)

        assertEquals(14, encounter.villainHealth)
        assertEquals(7, encounter.schemeLimit)
    }

    @Test
    fun `four players multiply the health and the threat limit`() {
        val encounter = rhinoFor(players = 4)

        assertEquals(56, encounter.villainHealth)
        assertEquals(28, encounter.schemeLimit)
    }

    @Test
    fun `a fixed value does not multiply`() {
        // Ultron's third stage starts with 3 threat on the scheme whatever the
        // player count, which is what base_threat_fixed means.
        val ultron = EncounterSetup(
            scheme = listOf(
                EncounterSide(
                    name = "Countdown to Oblivion",
                    stage = "3B",
                    value = 5,
                    perPlayer = true,
                    startingThreat = 3,
                    startingThreatPerPlayer = false,
                ),
            ),
            players = 3,
        )

        val encounter = Encounter.startOf(ultron)

        assertEquals(3, encounter.progress.threat)
        assertEquals(15, encounter.schemeLimit)
    }

    @Test
    fun `ending a round accelerates by the player count`() {
        val encounter = rhinoFor(players = 3).roundEnded()

        assertEquals(3, encounter.progress.threat)
        assertEquals(2, encounter.progress.round)
    }

    @Test
    fun `threat stops at the limit rather than running past it`() {
        val encounter = rhinoFor(players = 1).threatened(99)

        assertEquals(7, encounter.progress.threat)
        assertTrue(encounter.schemeComplete)
    }

    @Test
    fun `damage stops at the stage's health and never goes below zero`() {
        val maxed = rhinoFor(players = 1).damaged(99)
        assertEquals(14, maxed.progress.damage)
        assertTrue(maxed.villainDefeated)

        val healed = rhinoFor(players = 1).damaged(-5)
        assertEquals(0, healed.progress.damage)
    }

    @Test
    fun `flipping the villain resets the damage and reads the new health`() {
        val defeated = rhinoFor(players = 2).damaged(28)
        assertTrue(defeated.villainDefeated)

        val flipped = defeated.villainAdvanced()

        assertEquals(0, flipped.progress.damage)
        assertEquals("II", flipped.villainSide?.stage)
        assertEquals(30, flipped.villainHealth)
    }

    @Test
    fun `the last villain stage does not flip to nothing`() {
        val last = Encounter(rhino, EncounterProgress(villainIndex = 2))

        assertTrue(last.isFinalVillainStage)
        assertEquals(last, last.villainAdvanced())
    }

    @Test
    fun `a new scheme stage starts at its own printed threat`() {
        val ultron = EncounterSetup(
            scheme = listOf(
                EncounterSide("The Crimson Cowl", "1B", value = 3, perPlayer = true),
                EncounterSide(
                    name = "Assault on NORAD",
                    stage = "2B",
                    value = 10,
                    perPlayer = true,
                    startingThreat = 2,
                    startingThreatPerPlayer = true,
                ),
            ),
            players = 2,
        )

        val advanced = Encounter.startOf(ultron).threatened(6).schemeAdvanced()

        assertEquals(4, advanced.progress.threat)
        assertEquals(20, advanced.schemeLimit)
    }

    @Test
    fun `a starred value waits for the player rather than inventing one`() {
        // Juggernaut and Mojo print a star where the number goes. No card data
        // will ever fill that in, so the counter has to stay open.
        val juggernaut = Encounter.startOf(
            EncounterSetup(
                villain = listOf(
                    EncounterSide("Juggernaut", "I", value = null, perPlayer = false, starred = true),
                ),
                players = 2,
            ),
        )

        assertNull(juggernaut.villainHealth)
        assertFalse("cannot be defeated on a number nobody has", juggernaut.villainDefeated)
        assertEquals(40, juggernaut.withManualVillainHealth(40).villainHealth)
    }

    @Test
    fun `damage is unbounded while the health is unknown`() {
        val mojo = Encounter.startOf(
            EncounterSetup(
                villain = listOf(
                    EncounterSide("Mojo", "I", value = null, perPlayer = false, starred = true),
                ),
            ),
        )

        assertEquals(25, mojo.damaged(25).progress.damage)
    }

    @Test
    fun `a scheme with no threat limit is not complete before it starts`() {
        // The Brotherhood Strikes! prints no threat limit at all: it ends when
        // the villains are defeated. Read as a limit of zero it was "complete"
        // the instant the game began, in red, on the first screen of a game.
        val brotherhood = Encounter.startOf(
            EncounterSetup(
                scheme = listOf(
                    EncounterSide("The Brotherhood Strikes!", "1B", value = null, perPlayer = true),
                ),
                players = 2,
            ),
        )

        assertNull(brotherhood.schemeLimit)
        assertFalse(brotherhood.schemeComplete)
        assertEquals(9, brotherhood.threatened(9).progress.threat)
    }

    @Test
    fun `a scenario with nothing to count is not worth showing`() {
        assertFalse(EncounterSetup().isUsable)
        assertTrue(rhino.isUsable)
    }

    @Test
    fun `every move leaves the previous state untouched`() {
        // The whole reason this is immutable: the UI holds one of these and
        // must not have it change under it.
        val before = rhinoFor(players = 2)

        before.damaged(10).threatened(3).roundEnded()

        assertEquals(0, before.progress.damage)
        assertEquals(0, before.progress.threat)
        assertEquals(1, before.progress.round)
    }
}
