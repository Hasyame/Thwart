package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckFolderDao {

    /** The folders on the shelf, in the order a person looks for one: by name. */
    @Query("SELECT * FROM deck_folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<DeckFolderEntity>>

    @Query("SELECT * FROM deck_folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun getFolders(): List<DeckFolderEntity>

    @Query("SELECT * FROM deck_folders WHERE id = :id AND deletedAt IS NULL")
    suspend fun getFolder(id: String): DeckFolderEntity?

    @Upsert
    suspend fun upsert(folder: DeckFolderEntity)

    @Upsert
    suspend fun upsertAll(folders: List<DeckFolderEntity>)

    /** Everything, tombstones included: a backup restore starts from nothing. */
    @Query("DELETE FROM deck_folders")
    suspend fun deleteAll()
}
