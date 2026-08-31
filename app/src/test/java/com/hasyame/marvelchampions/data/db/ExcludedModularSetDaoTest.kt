package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.ExcludedModularSetDao
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
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
 * Modular sets the player owns the pack for but cannot field.
 *
 * This lives beside the collection rather than in the randomiser because it is
 * a fact about the shelf, not a per-draw preference: it has to survive leaving
 * the screen, and it has to be there on the very next draw.
 */
@RunWith(RobolectricTestRunner::class)
class ExcludedModularSetDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: ExcludedModularSetDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.excludedModularSetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing is excluded until the player says so`() = runTest {
        // Absence means owned: a fresh collection stores no rows at all, so
        // every set a pack contains is drawable without anyone opting in.
        assertTrue(dao.getExcludedCodes().isEmpty())
    }

    @Test
    fun `an exclusion outlives the screen that made it`() = runTest {
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))

        assertEquals(listOf("hydra_patrol"), dao.getExcludedCodes())
    }

    @Test
    fun `ticking a set back on removes it`() = runTest {
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))
        dao.include("hydra_patrol", 1_700_000_000_000L)

        assertTrue(dao.getExcludedCodes().isEmpty())
    }

    @Test
    fun `excluding the same set twice is not an error`() = runTest {
        // The row is keyed by the set code, so a double tap — or a restore over
        // an exclusion already present — replaces rather than throws.
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))

        assertEquals(1, dao.getExcludedCodes().size)
    }

    @Test
    fun `the collection screen sees a change as it happens`() = runTest {
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))

        val observed = dao.observeExcluded().first().map { it.setCode }
        assertEquals(listOf("hydra_patrol"), observed)
    }

    @Test
    fun `a restore replaces the list rather than merging into it`() = runTest {
        dao.exclude(ExcludedModularSetEntity("hydra_patrol"))

        dao.replaceAll(listOf(ExcludedModularSetEntity("zola")))

        assertEquals(listOf("zola"), dao.getExcludedCodes())
    }
}
