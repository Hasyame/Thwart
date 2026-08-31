package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import com.hasyame.marvelchampions.data.repository.PlayStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: PlayDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playDao()
    }

    @After
    fun tearDown() = database.close()

    private fun play(
        id: String,
        hero: String = "Spider-Man",
        heroCode: String = "01001a",
        scenario: String = "Rhino",
        scenarioCode: String = "01097",
        difficulty: String = "standard",
        aspects: String = "Justice",
        won: Boolean = true,
        at: Long = 1L,
        elapsedMillis: Long = 0L,
    ) = PlayEntity(
        id = id,
        playedAt = at,
        scenarioCode = scenarioCode,
        scenarioName = scenario,
        difficulty = difficulty,
        heroCode = heroCode,
        heroName = hero,
        aspects = aspects,
        won = won,
        elapsedMillis = elapsedMillis,
    )

    @Test
    fun `plays come back newest first`() = runTest {
        dao.insert(play("old", at = 100))
        dao.insert(play("new", at = 300))
        dao.insert(play("middle", at = 200))

        assertEquals(
            listOf("new", "middle", "old"),
            dao.observePlays().first().map { it.id },
        )
    }

    @Test
    fun `re-inserting the same play does not overwrite a recorded game`() = runTest {
        // A double tap or a retry must not rewrite history.
        dao.insert(play("p1", won = true))
        dao.insert(play("p1", won = false, hero = "Iron Man"))

        val stored = dao.observePlays().first().single()
        assertTrue(stored.won)
        assertEquals("Spider-Man", stored.heroName)
    }

    @Test
    fun `win rates are grouped by hero and by scenario`() = runTest {
        dao.insert(play("a", hero = "Spider-Man", heroCode = "h1", won = true))
        dao.insert(play("b", hero = "Spider-Man", heroCode = "h1", won = false))
        dao.insert(play("c", hero = "She-Hulk", heroCode = "h2", won = true))

        val byHero = PlayStats.heroes(dao.observeStatsRows().first()).associateBy { it.key }
        assertEquals(2, byHero.getValue("Spider-Man").played)
        assertEquals(1, byHero.getValue("Spider-Man").won)
        assertEquals(1, byHero.getValue("She-Hulk").won)

        // All three used the same scenario fixture.
        val byScenario = dao.observeByScenario().first().single()
        assertEquals(3, byScenario.played)
        assertEquals(2, byScenario.won)
    }

    @Test
    fun `grouping follows the code, and shows the name it was recorded under`() = runTest {
        // Two rows for one hero whose name changed between them — a
        // translation switch, say. They are one hero, not two.
        dao.insert(play("a", hero = "Spider-Man", heroCode = "h1"))
        dao.insert(play("b", hero = "L'Araignée", heroCode = "h1"))

        assertEquals(1, PlayStats.heroes(dao.observeStatsRows().first()).size)
        assertEquals(2, PlayStats.heroes(dao.observeStatsRows().first()).single().played)
    }

    @Test
    fun `a multiplayer game counts for every hero at the table`() = runTest {
        // The reported bug, end to end: only the first player reached the
        // statistics, so three of these four heroes were invisible.
        dao.insert(
            play("group").copy(
                heroCode = "h1",
                heroName = "Spider-Man",
                aspects = "Justice, Aggression, Protection, Leadership",
                otherHeroes = "Thor, Ms Marvel, Captain Marvel",
                players = 4,
                roster = listOf(
                    PlayHero("h1", "Spider-Man", "Justice"),
                    PlayHero("h2", "Thor", "Aggression"),
                    PlayHero("h3", "Ms Marvel", "Protection"),
                    PlayHero("h4", "Captain Marvel", "Leadership"),
                ),
            ),
        )

        val byHero = PlayStats.heroes(dao.observeStatsRows().first())
        assertEquals(4, byHero.size)
        byHero.forEach { assertEquals(1, it.played) }
    }

    @Test
    fun `the roster survives a round trip through the database`() = runTest {
        // It is stored as JSON in one column, so the converter is the only
        // thing standing between a recorded table and an empty one.
        val roster = listOf(
            PlayHero("h1", "Spider-Man", "Justice"),
            PlayHero("h2", "L'Araignée d'Acier", "Aggression, Leadership"),
        )
        dao.insert(play("p1").copy(roster = roster))

        assertEquals(roster, dao.getPlay("p1")!!.roster)
    }

    @Test
    fun `a play written before the roster existed still counts its heroes`() = runTest {
        // An empty roster is what every play recorded before this migration
        // has. The names are known even though the pairings are not.
        dao.insert(
            play("old").copy(
                heroName = "Spider-Man",
                otherHeroes = "Thor",
                players = 2,
                roster = emptyList(),
            ),
        )

        assertEquals(
            setOf("Spider-Man", "Thor"),
            PlayStats.heroes(dao.observeStatsRows().first()).map { it.key }.toSet(),
        )
    }

    @Test
    fun `a play is only marked as reported once it has been`() = runTest {
        dao.insert(play("p1"))
        assertFalse(dao.getPlay("p1")!!.reportedToBgg)

        dao.markReported("p1", 1_700_000_000_000L)
        assertTrue(dao.getPlay("p1")!!.reportedToBgg)
    }

    @Test
    fun `deleting a play removes it from the statistics too`() = runTest {
        dao.insert(play("a", won = true))
        dao.insert(play("b", won = false))

        dao.delete("b", 1_700_000_000_000L)

        assertEquals(1, dao.observePlays().first().size)
        assertEquals(1, PlayStats.heroes(dao.observeStatsRows().first()).single().won)
    }

    @Test
    fun `time played is totalled per hero`() = runTest {
        dao.insert(play("a", heroCode = "h1", elapsedMillis = 30 * 60_000L))
        dao.insert(play("b", heroCode = "h1", elapsedMillis = 50 * 60_000L))
        dao.insert(play("c", heroCode = "h2", hero = "She-Hulk", elapsedMillis = 0L))

        val byHero = PlayStats.heroes(dao.observeStatsRows().first()).associateBy { it.key }
        assertEquals(80 * 60_000L, byHero.getValue("Spider-Man").totalMillis)
        // An untimed game contributes nothing rather than breaking the sum.
        assertEquals(0L, byHero.getValue("She-Hulk").totalMillis)
    }
}
