package com.hasyame.marvelchampions.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.repository.FavouritePlayRepository
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.data.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavouritePlaysTest {
    @Test fun `stars sync without their play and tombstones survive repeated delivery`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java).allowMainThreadQueries().build()
        try {
            val codec = SyncRecordCodec(db.syncRecordDao(), AppPreferences(context), Json { ignoreUnknownKeys = true })
            val repository = FavouritePlayRepository(db, db.syncStateDao(), AutoSync(context, SyncSessionStore(context, SecretStore())), Dispatchers.Unconfined)
            val remote = SyncRecordDto("favourite_plays", "play-1", 1, "2026-09-19T00:00:00Z", body = buildJsonObject {
                put("playId", "play-1"); put("addedAt", 1000)
            })
            assertEquals(ApplyResult.Applied, codec.apply(remote, false, false, { "unused" }, "copy"))
            assertEquals(1000L, db.favouritePlayDao().getAll().single().addedAt)
            assertTrue(SyncCollection.DECLARED.split(',').contains("favourite_plays"))
            repository.toggle("play-1", false)
            assertTrue(db.favouritePlayDao().getAll().isEmpty())
            assertTrue(codec.read(SyncCollection.FAVOURITE_PLAYS, "play-1")!!.deleted)
            assertEquals(ApplyResult.KeptLocal, codec.apply(remote, true, false, { "unused" }, "copy"))
            val deletion = remote.copy(revision = 2, deleted = true, body = null)
            repeat(2) { codec.apply(deletion, false, false, { "unused" }, "copy") }
            assertTrue(db.favouritePlayDao().getAll().isEmpty())
            repository.toggle("play-1", true)
            assertFalse(codec.read(SyncCollection.FAVOURITE_PLAYS, "play-1")!!.deleted)
            assertEquals(1, codec.readAll(SyncCollection.FAVOURITE_PLAYS).size)
        } finally { db.close() }
    }
}
