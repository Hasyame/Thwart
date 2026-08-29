package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a game is a complete setup, and when it is not.
 *
 * Written after a randomiser draw of an Expert difficulty stopped working. The
 * draw handed over the difficulty but not the Standard set rolled with it, so
 * the setup was incomplete, start() refused it, and autoStart did nothing at
 * all: the player was dropped on the setup page with no error and no
 * explanation, being asked for a deck the randomiser never needed.
 *
 * Nothing failed loudly, which is why this is a test rather than a comment.
 */
class SessionCanStartTest {

    private fun session(
        difficulty: Difficulty,
        standardSet: Difficulty? = null,
        heroes: List<SessionHero> = listOf(SessionHero("01001a", "leadership")),
        scenario: String? = "01094",
    ) = GameSessionUiState(
        scenarioCode = scenario,
        difficulty = difficulty.name.lowercase(),
        standardSet = standardSet?.name?.lowercase(),
        heroes = heroes,
    )

    @Test
    fun `a standard game needs no companion set`() {
        assertTrue(session(Difficulty.STANDARD_I).canStart)
    }

    @Test
    fun `an expert game without its standard set is not a setup`() {
        assertFalse(session(Difficulty.EXPERT_I).canStart)
        assertFalse(session(Difficulty.EXPERT_II).canStart)
    }

    @Test
    fun `an expert game with its standard set can start`() {
        assertTrue(session(Difficulty.EXPERT_I, Difficulty.STANDARD_I).canStart)
        assertTrue(session(Difficulty.EXPERT_II, Difficulty.STANDARD_III).canStart)
    }

    @Test
    fun `a game still needs a scenario and somebody to play it`() {
        assertFalse(session(Difficulty.STANDARD_I, scenario = null).canStart)
        assertFalse(session(Difficulty.STANDARD_I, heroes = emptyList()).canStart)
    }
}
