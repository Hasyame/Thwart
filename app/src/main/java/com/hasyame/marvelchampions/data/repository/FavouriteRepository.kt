package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.FavouriteDao
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cards the player has starred.
 *
 * Kept apart from the card table on purpose: that table is a cache of
 * MarvelCDB and is wiped on every sync, so a flag stored there would vanish
 * with the next card update.
 */
@Singleton
class FavouriteRepository @Inject constructor(
    private val favouriteDao: FavouriteDao,
    private val syncStateDao: SyncStateDao,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeCodes(): Flow<Set<String>> = favouriteDao.observeCodes().map { it.toSet() }

    suspend fun toggle(cardCode: String, favourite: Boolean) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        if (favourite) {
            // Starring a card that was starred and unstarred before writes over
            // the tombstone rather than leaving one, which is what makes the
            // row visible again.
            favouriteDao.add(
                FavouriteCardEntity(cardCode = cardCode, addedAt = now, updatedAt = now),
            )
        } else {
            favouriteDao.remove(cardCode, now)
        }
        syncStateDao.markDirty(SyncCollection.FAVOURITE_CARDS.key, cardCode)
    }
}
