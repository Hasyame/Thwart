package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.FavouritePlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouritePlayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favourite: FavouritePlayEntity)

    @Query(
        "UPDATE favourite_plays SET deletedAt = :now, updatedAt = :now WHERE playId = :playId",
    )
    suspend fun remove(playId: String, now: Long)

    @Query("SELECT playId FROM favourite_plays WHERE deletedAt IS NULL")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM favourite_plays WHERE deletedAt IS NULL ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavouritePlayEntity>

    /** For a restore, which replaces rather than merges. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM favourite_plays")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(favourites: List<FavouritePlayEntity>)
}
