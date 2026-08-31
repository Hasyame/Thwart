package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.FavouriteRepository
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A write says when it happened, and says it has not been pushed.
 *
 * Both facts are recorded by the repository rather than by the call site, and
 * that is the whole point: a row saved without a timestamp is a row the next
 * device never hears about, and nothing anywhere would report it. The bug would
 * surface months later as "my tablet is missing three games".
 */
@RunWith(RobolectricTestRunner::class)
class SyncStampingTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var favourites: FavouriteRepository
    private lateinit var collection: CollectionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()

        favourites = FavouriteRepository(
            database.favouriteDao(),
            database.syncStateDao(),
            Dispatchers.Unconfined,
        )
        collection = CollectionRepository(
            database.packDao(),
            database.ownedPackDao(),
            database.excludedModularSetDao(),
            database.excludedScenarioDao(),
            database.cardDao(),
            database.syncStateDao(),
            SetNameOverrides(context, Json { ignoreUnknownKeys = true }, Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `starring a card stamps it and queues it`() = runTest {
        val before = System.currentTimeMillis()
        favourites.toggle("01001", favourite = true)
        val after = System.currentTimeMillis()

        val stamp = database.favouriteDao().getAll().single().updatedAt
        assertTrue("updatedAt was $stamp, not between $before and $after", stamp in before..after)
        assertTrue(isDirty(SyncCollection.FAVOURITE_CARDS, "01001"))
    }

    @Test
    fun `unstarring it stamps the tombstone and queues that`() = runTest {
        favourites.toggle("01001", favourite = true)
        database.syncStateDao().markSynced(SyncCollection.FAVOURITE_CARDS.key, "01001", 41)

        favourites.toggle("01001", favourite = false)

        // The delete is a change like any other and has to be pushed, or the
        // other device keeps the card starred for ever.
        assertTrue(isDirty(SyncCollection.FAVOURITE_CARDS, "01001"))
    }

    @Test
    fun `owning a pack, and giving it away, are both recorded`() = runTest {
        collection.setOwned("core", owned = true)
        assertTrue(database.ownedPackDao().getOwned().single().updatedAt > 0)
        assertTrue(isDirty(SyncCollection.OWNED_PACKS, "core"))

        database.syncStateDao().markSynced(SyncCollection.OWNED_PACKS.key, "core", 12)
        assertFalse(isDirty(SyncCollection.OWNED_PACKS, "core"))

        collection.setOwned("core", owned = false)
        assertTrue(isDirty(SyncCollection.OWNED_PACKS, "core"))
    }

    @Test
    fun `changing how many copies is a change`() = runTest {
        collection.setQuantity("core", 1)
        database.syncStateDao().markSynced(SyncCollection.OWNED_PACKS.key, "core", 12)

        collection.setQuantity("core", 2)

        assertEquals(2, database.ownedPackDao().getOwned().single().quantity)
        assertTrue(isDirty(SyncCollection.OWNED_PACKS, "core"))
    }

    @Test
    fun `ticking a set or a scenario off is recorded under its own collection`() = runTest {
        collection.setModularSetExcluded("bomb_scare", excluded = true)
        collection.setScenarioExcluded("wrecking_crew", excluded = true)

        assertTrue(isDirty(SyncCollection.EXCLUDED_MODULAR_SETS, "bomb_scare"))
        assertTrue(isDirty(SyncCollection.EXCLUDED_SCENARIOS, "wrecking_crew"))
        // Kept apart, like the tables themselves. They share a name space in the
        // card database, so one queue for both would let a scenario's change
        // overwrite a modular set's.
        assertEquals(
            null,
            database.syncStateDao().get(SyncCollection.EXCLUDED_SCENARIOS.key, "bomb_scare"),
        )
    }

    @Test
    fun `a queued change keeps the revision the server gave it`() = runTest {
        val dao = database.syncStateDao()
        dao.markSynced(SyncCollection.PLAYS.key, "play-1", 1841)

        dao.markDirty(SyncCollection.PLAYS.key, "play-1")

        // The revision is what the next push quotes as `baseRevision`, and it
        // is how the server tells a clean write from one that overwrote
        // somebody else's. Losing it on every local edit would turn every
        // reported conflict into a silent one.
        val state = dao.get(SyncCollection.PLAYS.key, "play-1")
        assertNotNull(state)
        assertEquals(1841L, state!!.serverRevision)
        assertTrue(state.dirty)
    }

    @Test
    fun `a record nobody has heard of is dirty by absence`() = runTest {
        // Rows written before sync was ever switched on have no bookkeeping at
        // all, and they still have to be uploaded. Absence means dirty; it is
        // not a state that needs writing down.
        assertEquals(null, database.syncStateDao().get(SyncCollection.PLAYS.key, "play-99"))
        assertTrue(database.syncStateDao().dirtyRecords().isEmpty())
    }

    private suspend fun isDirty(collection: SyncCollection, id: String): Boolean =
        database.syncStateDao().get(collection.key, id)?.dirty ?: true
}
