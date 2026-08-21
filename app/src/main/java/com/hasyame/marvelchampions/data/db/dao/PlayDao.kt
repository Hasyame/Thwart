package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import kotlinx.coroutines.flow.Flow

/** One row of a win-rate table. */
data class WinRateRow(
    val key: String,
    val played: Int,
    val won: Int,
    /** Total time spent on these games, so "hours with this hero" is answerable. */
    val totalMillis: Long = 0,
)

@Dao
interface PlayDao {

    /**
     * IGNORE rather than REPLACE. Ids are stable, and a duplicate insert is a
     * double tap or a retry, not an instruction to overwrite a recorded game.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(play: PlayEntity)

    @Query("SELECT * FROM plays ORDER BY playedAt DESC")
    fun observePlays(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun getPlay(id: String): PlayEntity?

    @Query("DELETE FROM plays WHERE id = :id")
    suspend fun delete(id: String)

    /** Every play's photo list, for the sweep that deletes the rest. */
    @Query("SELECT photos FROM plays WHERE photos != ''")
    suspend fun photoLists(): List<String>

    @Query("UPDATE plays SET reportedToBgg = 1 WHERE id = :id")
    suspend fun markReported(id: String)

    // Counted in SQL where a play is one row of the answer — a game happened at
    // one scenario, one difficulty, and one table size.

    /**
     * The rows behind the per-hero, per-aspect and hero-with-aspect tables,
     * which are counted in memory instead.
     *
     * A group game holds several heroes and several aspects in a single row, so
     * `GROUP BY heroCode` could only ever see the first of them: a four-player
     * game credited one hero and silently dropped the other three. Splitting a
     * roster inside SQLite would take a recursive CTE to do a worse job than a
     * loop, and only these seven columns are carried rather than whole plays.
     */
    @Query(
        """
        SELECT heroCode, heroName, aspects, otherHeroes, roster, won, elapsedMillis
        FROM plays ORDER BY playedAt DESC
        """,
    )
    fun observeStatsRows(): Flow<List<PlayStatsRow>>

    @Query(
        """
        SELECT scenarioName AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY scenarioCode ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByScenario(): Flow<List<WinRateRow>>

    @Query(
        """
        SELECT difficulty AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY difficulty ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByDifficulty(): Flow<List<WinRateRow>>

    /**
     * Solo against multiplayer.
     *
     * Worth its own split because the win rates differ enormously, and a
     * single blended figure describes neither: a player who wins two thirds
     * solo and a third in a group is not a 50% player at anything.
     */
    @Query(
        """
        SELECT CASE WHEN players <= 1 THEN 'solo' ELSE 'group' END AS `key`,
               COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY `key` ORDER BY played DESC
        """,
    )
    fun observeBySoloOrGroup(): Flow<List<WinRateRow>>

    /** For a restore, which replaces rather than merges. */
    @Query("DELETE FROM plays")
    suspend fun deleteAll()

    @Query("SELECT * FROM plays")
    suspend fun getAllPlays(): List<PlayEntity>
}

/**
 * A play reduced to what the hero and aspect tables need.
 *
 * [roster] is the seat-by-seat record and is empty on plays written before it
 * existed; the four fields beside it are what those older rows have, and
 * `PlayStats.seats` recovers what it can from them.
 */
data class PlayStatsRow(
    val heroCode: String,
    val heroName: String,
    val aspects: String,
    val otherHeroes: String,
    val roster: List<PlayHero>,
    val won: Boolean,
    val elapsedMillis: Long,
)
