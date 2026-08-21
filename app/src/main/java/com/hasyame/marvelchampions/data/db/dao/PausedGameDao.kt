package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PausedGameDao {

    /** The saved game, if there is one. There is at most one. */
    @Query("SELECT * FROM paused_games ORDER BY savedAt DESC LIMIT 1")
    fun observe(): Flow<PausedGameEntity?>

    @Query("SELECT * FROM paused_games ORDER BY savedAt DESC LIMIT 1")
    suspend fun current(): PausedGameEntity?

    /**
     * Saves one, replacing whatever was there.
     *
     * Putting a second game down means the first is not coming back, and a
     * paused game nobody resumes is a row that outlives its usefulness.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: PausedGameEntity)

    /** The paused game's photo list, for the same sweep. */
    @Query("SELECT photos FROM paused_games WHERE photos != ''")
    suspend fun photoLists(): List<String>

    @Query("DELETE FROM paused_games")
    suspend fun clear()
}
