package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.domain.achievements.Unlock
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game is over the moment it is written down.
 *
 * It was not. The session stayed in the playing phase until the result was
 * answered, so walking to the Decks tab and back landed on the finished game
 * again, with "forget this game" the only way out of a game that was already
 * in the history and already sent to BoardGameGeek.
 *
 * The transitions are functions on the state, so what the screens do to a
 * session is testable without a view model, a database or a device.
 */
class GameEndsWhenFiledTest {

    private val seats = listOf(
        SessionHero("01001a", "justice", deckId = "deck-1"),
        SessionHero("01010a", "leadership", deckId = "deck-2"),
    )

    private val played = GameSessionUiState(
        phase = SessionPhase.PLAYING,
        scenarioCode = "fne_s1_musee__fne_villain_electro",
        difficulty = "expert_i",
        standardSet = "standard_ii",
        heroes = seats,
        modularSetCodes = listOf("bomb_scare", "goblin_gimmicks"),
        timer = TimerState().start(0L),
        elapsedMillis = 2_700_000,
        firstPlayerIndex = 1,
        photos = listOf("table.jpg"),
        isFinishing = true,
        lastPlay = PlayEntity(
            id = "play-1",
            playedAt = 1_700_000_000_000L,
            scenarioCode = "fne_s1_musee__fne_villain_electro",
            scenarioName = "Art Museum Heist",
            difficulty = "expert_i",
            heroCode = "01001a",
            heroName = "Spider-Man",
            aspects = "justice, leadership",
            won = true,
        ),
    )

    @Test
    fun `filing the game clears the table and leaves the setup`() {
        val after = played.filed()

        assertEquals(SessionPhase.SETUP, after.phase)
        assertNull(after.scenarioCode)
        assertEquals(emptyList<SessionHero>(), after.heroes)
        assertEquals(emptyList<String>(), after.modularSetCodes)
        assertEquals(emptyList<String>(), after.photos)
        assertEquals(0, after.elapsedMillis)
        assertNull(after.firstPlayerIndex)
        assertTrue(after.timer.elapsedAt(0L) == 0L)
        // A second tap on a result cannot file a second play; a new game must
        // still be finishable.
        assertTrue(!after.isFinishing)
    }

    @Test
    fun `what the result page shows survives the game ending`() {
        val after = played.filed()

        assertEquals("play-1", after.lastPlay?.id)
        assertEquals("fne_s1_musee__fne_villain_electro", after.lastTable?.scenarioCode)
    }

    @Test
    fun `the table played is kept whole, villain and all`() {
        val table = played.filed().lastTable

        assertEquals("fne_s1_musee__fne_villain_electro", table?.scenarioCode)
        assertEquals("expert_i", table?.difficulty)
        assertEquals("standard_ii", table?.standardSet)
        assertEquals(seats, table?.heroes)
        assertEquals(listOf("bomb_scare", "goblin_gimmicks"), table?.modularSetCodes)
    }

    @Test
    fun `playing again lays the same game out, not started, with a note`() {
        val again = played.filed().laidOutAgain()

        assertEquals(SessionPhase.SETUP, again.phase)
        assertEquals("fne_s1_musee__fne_villain_electro", again.scenarioCode)
        assertEquals("expert_i", again.difficulty)
        assertEquals("standard_ii", again.standardSet)
        // The decks, so a game played from a drafted deck stays a draft game.
        assertEquals(listOf("deck-1", "deck-2"), again.heroes.map { it.deckId })
        assertEquals(listOf("bomb_scare", "goblin_gimmicks"), again.modularSetCodes)
        assertEquals(0, again.elapsedMillis)
        assertTrue(again.timer.elapsedAt(0L) == 0L)
        assertTrue(again.playAgainNote)
        // The result is put away: this is a new game, not the old one.
        assertNull(again.lastPlay)
    }

    @Test
    fun `a new game leaves nothing of the last one`() {
        val empty = played.filed().emptied()

        assertEquals(SessionPhase.SETUP, empty.phase)
        assertNull(empty.scenarioCode)
        assertEquals(emptyList<SessionHero>(), empty.heroes)
        assertNull(empty.lastPlay)
        assertNull(empty.lastTable)
        assertTrue(!empty.playAgainNote)
    }

    @Test
    fun `putting the result away leaves the ended session alone`() {
        val after = played.filed().withoutResult()

        assertNull(after.lastPlay)
        assertEquals(emptyList<Unlock>(), after.unlocked)
        // The game ended when it was filed, so there is nothing left to end.
        assertEquals(SessionPhase.SETUP, after.phase)
        assertNull(after.scenarioCode)
    }

    @Test
    fun `a session that filed nothing has no table to play again`() {
        val empty = GameSessionUiState().filed()

        assertNull(empty.lastTable)
        assertEquals(SessionPhase.SETUP, empty.laidOutAgain().phase)
    }
}
