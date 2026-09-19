package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.FavouritePlayEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.SyncTrigger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Recorded games starred locally or through the shared account. */
@Singleton
class FavouritePlayRepository @Inject constructor(
    database: MarvelChampionsDatabase,
    private val syncStateDao: SyncStateDao,
    private val autoSync: AutoSync,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val favouriteDao = database.favouritePlayDao()

    fun observeIds(): Flow<Set<String>> = favouriteDao.observeIds().map { it.toSet() }

    suspend fun toggle(playId: String, favourite: Boolean) = withContext(ioDispatcher) {
        syncStateDao.transaction {
            val now = System.currentTimeMillis()
            if (favourite) {
                // Starring a play that was starred and unstarred before writes over
                // the tombstone rather than leaving one, which is what makes the
                // row visible again.
                favouriteDao.add(
                    FavouritePlayEntity(playId = playId, addedAt = now, updatedAt = now),
                )
            } else {
                favouriteDao.remove(playId, now)
            }
            syncStateDao.markDirty(SyncCollection.FAVOURITE_PLAYS.key, playId)
        }
        autoSync.after(SyncTrigger.PLAY_FAVOURITED)
    }
}
