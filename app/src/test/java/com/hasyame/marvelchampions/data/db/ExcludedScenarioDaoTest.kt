package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.ExcludedScenarioDao
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios the player owns the pack for but has not got.
 *
 * A box bought second hand, or split with a friend, can be missing scenarios as
 * easily as modular sets. Unticking one means "I have not got it", so it has to
 * disappear from the draw *and* from setting a game up by hand — the same fact
 * either way.
 */
@RunWith(RobolectricTestRunner::class)
class ExcludedScenarioDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: ExcludedScenarioDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.excludedScenarioDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing is missing until the player says so`() {
        // Absence means owned, so a fresh collection stores no rows at all and
        // nobody has to opt in to having bought the game.
        runTest { assertTrue(dao.getExcludedCodes().isEmpty()) }
    }

    @Test
    fun `a scenario ticked off outlives the screen`() = runTest {
        dao.exclude(ExcludedScenarioEntity("mutagen_formula"))

        assertEquals(listOf("mutagen_formula"), dao.getExcludedCodes())
    }

    @Test
    fun `ticking it back on removes it`() = runTest {
        dao.exclude(ExcludedScenarioEntity("mutagen_formula"))
        dao.include("mutagen_formula", 1_700_000_000_000L)

        assertTrue(dao.getExcludedCodes().isEmpty())
    }

    @Test
    fun `the same scenario twice is not an error`() = runTest {
        dao.exclude(ExcludedScenarioEntity("zola"))
        dao.exclude(ExcludedScenarioEntity("zola"))

        assertEquals(1, dao.getExcludedCodes().size)
    }

    @Test
    fun `the collection screen sees a change as it happens`() = runTest {
        dao.exclude(ExcludedScenarioEntity("zola"))

        assertEquals(
            listOf("zola"),
            dao.observeExcluded().first().map { it.scenarioCode },
        )
    }

    @Test
    fun `scenarios and modular sets are kept apart`() = runTest {
        // They share a name space in the card database — a set code is a set
        // code — so storing them in one table would let unticking a scenario
        // silently remove a modular set of the same name.
        dao.exclude(ExcludedScenarioEntity("wrecking_crew"))
        database.excludedModularSetDao().getExcludedCodes().let {
            assertTrue("excluding a scenario touched the modular sets", it.isEmpty())
        }
    }
}
