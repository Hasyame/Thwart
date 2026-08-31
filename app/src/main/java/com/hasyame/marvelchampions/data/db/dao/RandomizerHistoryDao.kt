package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RandomizerHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RandomizerHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<RandomizerHistoryEntity>)

    @Query("SELECT * FROM randomizer_history WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<RandomizerHistoryEntity>>

    @Query("SELECT * FROM randomizer_history WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getHistory(): List<RandomizerHistoryEntity>

    /** Scenarios the user has marked as beaten, for the "exclude beaten" filter. */
    @Query(
        """
        SELECT DISTINCT scenarioCode FROM randomizer_history
        WHERE beaten = 1 AND deletedAt IS NULL
        """,
    )
    fun observeBeatenScenarios(): Flow<List<String>>

    @Query(
        """
        UPDATE randomizer_history SET beaten = :beaten, updatedAt = :now
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setBeaten(id: String, beaten: Boolean, now: Long)

    @Query("UPDATE randomizer_history SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long)

    /** For a restore, which replaces rather than merges. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM randomizer_history")
    suspend fun clear()
}
