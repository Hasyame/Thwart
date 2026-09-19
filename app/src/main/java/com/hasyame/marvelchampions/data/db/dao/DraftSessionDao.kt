package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hasyame.marvelchampions.data.db.entity.DraftSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftSessionDao {

    /** Keep all writes of one logical operation on the same Room transaction. */
    @Transaction
    suspend fun <T> transaction(block: suspend () -> T): T = block()


    @Query("SELECT * FROM draft_sessions ORDER BY updatedAt DESC LIMIT 1")
    fun observe(): Flow<DraftSessionEntity?>

    @Query("SELECT * FROM draft_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun current(): DraftSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DraftSessionEntity)

    @Query("DELETE FROM draft_sessions")
    suspend fun clear()
}
