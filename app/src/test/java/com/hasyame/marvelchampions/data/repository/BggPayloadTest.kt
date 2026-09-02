package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What actually reaches BoardGameGeek.
 *
 * A three-handed game was arriving as a solo play, because on BGG the number
 * of players *is* the number of player rows and the app was only ever sending
 * one. That is not visible from inside the app — the play looks right in the
 * history and wrong only on the website — so it is pinned here.
 */
class BggPayloadTest {

    /** 6 August 2026, 21:30 local, after a 75-minute game. */
    private val finishedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .parse("2026-08-06 21:30")!!
        .time

    private fun play(
        roster: List<PlayHero> = emptyList(),
        elapsedMinutes: Long = 75,
    ) = PlayEntity(
        id = "p1",
        playedAt = finishedAt,
        scenarioCode = "zola",
        scenarioName = "Zola",
        difficulty = "Standard",
        heroCode = "01001",
        heroName = "Spider-Man",
        aspects = "Justice",
        roster = roster,
        won = true,
        elapsedMillis = elapsedMinutes * 60_000,
        victoryPoints = 4,
    )

    private fun bgg(entity: PlayEntity) = entity.toBggPlay("benoit")

    @Test
    fun `a solo game sends one seat`() {
        val payload = bgg(play(roster = listOf(PlayHero("01001", "Spider-Man", "Justice"))))

        assertEquals(1, payload.players.size)
        assertEquals("benoit", payload.players.single().username)
    }

    @Test
    fun `however many hands are played, one person is reported`() {
        // This briefly sent a seat per hero, because BGG counts player rows and
        // a group game was arriving as solo. That confused heroes with people.
        // Two-handed solo is one person holding two decks, and the second row
        // was a player who does not exist. At a real table the others have
        // their own accounts and log the game themselves.
        val payload = bgg(
            play(
                roster = listOf(
                    PlayHero("01001", "Spider-Man", "Justice"),
                    PlayHero("01029", "She-Hulk", "Aggression"),
                    PlayHero("01004", "Iron Man", "Leadership"),
                ),
            ),
        )

        assertEquals(1, payload.players.size)
        assertEquals("benoit", payload.players.single().name)
    }

    @Test
    fun `the seat is the account holder's own`() {
        val payload = bgg(
            play(
                roster = listOf(
                    PlayHero("01001", "Spider-Man", "Justice"),
                    PlayHero("01029", "She-Hulk", "Aggression"),
                ),
            ),
        )

        assertEquals("benoit", payload.players.single().username)
    }

    @Test
    fun `the heroes are still named, in the comment`() {
        // Nothing is lost by the single seat: what was played is recorded, in
        // the one field BGG keeps free text in.
        val payload = bgg(
            play(
                roster = listOf(
                    PlayHero("01001", "Spider-Man", "Justice"),
                    PlayHero("01029", "She-Hulk", "Aggression"),
                ),
            ),
        )

        assertTrue(payload.comment.contains("Spider-Man"))
    }

    @Test
    fun `the play is filed on the day it finished`() {
        assertEquals("2026-08-06", bgg(play()).playedOn)
    }

    @Test
    fun `the comment carries the start and finish times`() {
        // BGG has no field for either, so the comment is the only place they
        // can go. 75 minutes before 21:30 is 20:15.
        val comment = bgg(play(elapsedMinutes = 75)).comment

        assertTrue(comment, "Played 2026-08-06, 20:15–21:30" in comment)
    }

    @Test
    fun `where it was played travels with the play`() {
        assertEquals("Chez Marc", bgg(play().copy(location = "Chez Marc")).location)
    }

    @Test
    fun `no location set sends an empty one rather than inventing something`() {
        assertEquals("", bgg(play()).location)
    }

    @Test
    fun `length is still reported in minutes`() {
        assertEquals(75, bgg(play(elapsedMinutes = 75)).lengthMinutes)
    }

    @Test
    fun `a roster the app never recorded still sends the one seat it knows`() {
        // Plays logged before the roster column existed have an empty roster.
        // Dropping to zero seats would be worse than reporting a solo game.
        val payload = bgg(play(roster = emptyList()))

        assertEquals(1, payload.players.size)
        assertEquals("benoit", payload.players.single().username)
    }
}
