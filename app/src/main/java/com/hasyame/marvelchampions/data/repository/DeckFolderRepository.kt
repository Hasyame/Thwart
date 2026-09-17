package com.hasyame.marvelchampions.data.repository

import androidx.room.withTransaction
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folders on the shelf of decks.
 *
 * The folder holds the list of decks, not the deck its folder, so every
 * change here is one folder written whole with a fresh `updatedAt`, which
 * is what the merge compares and what marks it for sync. The same shape as
 * the web client's `folders.ts`, so the two shelves agree.
 */
@Singleton
class DeckFolderRepository @Inject constructor(
    private val database: MarvelChampionsDatabase,
    private val autoSync: AutoSync,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val dao get() = database.deckFolderDao()

    fun observeFolders(): Flow<List<DeckFolderEntity>> = dao.observeFolders()

    suspend fun create(name: String): String? = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return@withContext null
        }
        val now = System.currentTimeMillis()
        val id = "$ID_PREFIX${UUID.randomUUID()}"
        save(DeckFolderEntity(id = id, name = trimmed, deckIds = emptyList(), createdAt = now, updatedAt = now))
        id
    }

    suspend fun rename(id: String, name: String) = withContext(ioDispatcher) {
        val trimmed = name.trim()
        val folder = dao.getFolder(id) ?: return@withContext
        if (trimmed.isEmpty() || trimmed == folder.name) {
            return@withContext
        }
        save(folder.copy(name = trimmed))
    }

    /** Removes the folder. Its decks stay on the shelf, in no folder. */
    suspend fun delete(id: String) = withContext(ioDispatcher) {
        val folder = dao.getFolder(id) ?: return@withContext
        val now = System.currentTimeMillis()
        save(folder.copy(deletedAt = now), now)
    }

    /**
     * Puts a deck in a folder, or in none, taking it out of any other first.
     * One transaction, so a sync between the two writes cannot show the deck
     * in two folders or in neither.
     */
    suspend fun moveDeck(deckId: String, folderId: String?) = withContext(ioDispatcher) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            dao.getFolders().forEach { folder ->
                val inIt = deckId in folder.deckIds
                if (folder.id == folderId && !inIt) {
                    save(folder.copy(deckIds = folder.deckIds + deckId), now)
                } else if (folder.id != folderId && inIt) {
                    save(folder.copy(deckIds = folder.deckIds - deckId), now)
                }
            }
        }
    }

    /** A deck that is deleted leaves whatever folder held it, so the folder does not keep a ghost. */
    suspend fun forgetDeck(deckId: String) = moveDeck(deckId, null)

    private suspend fun save(folder: DeckFolderEntity, at: Long = System.currentTimeMillis()) {
        dao.upsert(folder.copy(updatedAt = at))
        database.syncStateDao().markDirty(SyncCollection.DECK_FOLDERS.key, folder.id)
        autoSync.after(SyncTrigger.DECK_ADDED)
    }

    companion object {
        /** The same prefix the web client gives its folders. */
        const val ID_PREFIX = "folder-"

        /** The folder a deck is in, or null. The first wins if data disagrees. */
        fun folderOf(folders: List<DeckFolderEntity>, deckId: String): DeckFolderEntity? =
            folders.firstOrNull { deckId in it.deckIds }
    }
}
