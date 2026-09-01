package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.domain.play.EncounterProgress
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import com.hasyame.marvelchampions.domain.play.EncounterSide
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Coming back to a game that was put down.
 *
 * The whole point of a long break is picking the game up where it stood, and
 * two separate things were throwing that away. The clock was reset to zero the
 * moment play resumed, so an hour already played was gone; and the tracker's
 * counters were never written down at all, so the villain came back at full
 * health however long the table had spent knocking it down.
 *
 * Both are silent losses: nothing crashes, the game simply starts again and the
 * player is left to work out what happened.
 */
class LongBreakResumeTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = 1_700_000_000_000L

    // ------------------------------------------------------------- the clock --

    @Test
    fun `resuming keeps the time already played`() {
        // Put down after 42 minutes.
        val putAway = TimerState(accumulatedMillis = 42 * 60_000L)

        // What resuming does now: carry on from the clock the game has.
        val resumed = putAway.start(now)

        assertEquals(42 * 60_000L, resumed.elapsedAt(now))
        assertEquals(43 * 60_000L, resumed.elapsedAt(now + 60_000L))
    }

    @Test
    fun `the old way lost the time, which is the bug`() {
        val putAway = TimerState(accumulatedMillis = 42 * 60_000L)

        // What it used to do: a brand new clock, started now.
        val broken = TimerState().start(now)

        assertEquals(0L, broken.elapsedAt(now))
        assertNotEquals(putAway.elapsedAt(now), broken.elapsedAt(now))
    }

    @Test
    fun `a game that was never paused still starts from zero`() {
        // The same call has to serve a brand new game, where the timer is
        // fresh. If this drifted, every new game would start part-played.
        val fresh = TimerState()

        assertEquals(0L, fresh.start(now).elapsedAt(now))
        assertEquals(90_000L, fresh.start(now).elapsedAt(now + 90_000L))
    }

    // ---------------------------------------------------------- the counters --

    @Test
    fun `the counters survive being written down and read back`() {
        val progress = EncounterProgress(
            villainIndex = 1,
            damage = 9,
            schemeIndex = 0,
            threat = 7,
            round = 4,
            manualVillainHealth = null,
            manualSchemeLimit = 12,
        )

        val text = json.encodeToString(EncounterProgress.serializer(), progress)
        val back = json.decodeFromString(EncounterProgress.serializer(), text)

        // Every field, not a sample. Threat and round are the two that were
        // never stored anywhere before, and a game resumed without them starts
        // the scheme empty and the round count at one.
        assertEquals(progress, back)
        assertEquals(7, back.threat)
        assertEquals(4, back.round)
    }

    @Test
    fun `a villain part way down comes back part way down`() {
        val setup = EncounterSetup(
            villain = listOf(
                EncounterSide(name = "Rhino", stage = "I", value = 14, perPlayer = true),
            ),
            scheme = listOf(
                EncounterSide(name = "Breakout", stage = "1", value = 8, perPlayer = true),
            ),
            players = 2,
        )
        val played = Encounter.startOf(setup)
            .damaged(11)
            .threatened(5)
            .roundEnded()

        val text = json.encodeToString(EncounterProgress.serializer(), played.progress)
        val resumed = Encounter(
            setup = setup,
            progress = json.decodeFromString(EncounterProgress.serializer(), text),
        )

        assertEquals(played.progress, resumed.progress)
        assertEquals(11, resumed.progress.damage)
        assertEquals(played.progress.threat, resumed.progress.threat)
        assertEquals(played.progress.round, resumed.progress.round)

        // And it is genuinely not a fresh board.
        assertNotEquals(Encounter.startOf(setup).progress, resumed.progress)
    }

    @Test
    fun `the villain figures come from the tracker, not from the table`() {
        val setup = EncounterSetup(
            villain = listOf(
                EncounterSide(name = "Rhino", stage = "I", value = 14, perPlayer = false),
                EncounterSide(name = "Rhino", stage = "II", value = 16, perPlayer = false),
            ),
            scheme = emptyList(),
            players = 1,
        )
        val played = Encounter.startOf(setup).damaged(9)

        // What the row records while the table types nothing at all.
        val life = played.villainHealth!! - played.progress.damage
        val stage = played.progress.villainIndex + 1

        assertEquals(5, life)
        assertEquals(1, stage)

        // And after the villain flips, the stage follows without being asked.
        val flipped = played.villainAdvanced()
        assertEquals(2, flipped.progress.villainIndex + 1)
    }

    @Test
    fun `a break saved with the tracker off is stored as nothing`() {
        // Empty rather than a default-filled object, so "the tracker was off"
        // and "the tracker was on and everything was at zero" stay different
        // facts, and the hand-written figures are used for the first.
        val storedWhenOff = ""

        assertEquals(
            null,
            storedWhenOff.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    json.decodeFromString(EncounterProgress.serializer(), it)
                }.getOrNull()
            },
        )
    }

    @Test
    fun `text that will not parse falls back rather than crashing`() {
        // A paused row travels in an export and comes back from a file that has
        // been outside the app. A ruined value must not take the screen down on
        // the one path a player uses to rescue a game.
        val ruined = "{ this is not json"

        assertEquals(
            null,
            runCatching {
                json.decodeFromString(EncounterProgress.serializer(), ruined)
            }.getOrNull(),
        )
    }
}
