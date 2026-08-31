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

    @Query("SELECT * FROM plays WHERE deletedAt IS NULL ORDER BY playedAt DESC")
    fun observePlays(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPlay(id: String): PlayEntity?

    @Query("UPDATE plays SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long)

    /**
     * Every play's photo list, for the sweep that deletes the rest.
     *
     * Tombstoned plays are **included** here, which is the one place a deleted
     * row is deliberately still read. A photograph is a file on disk, and
     * deleting the file the moment the play is tombstoned would make the delete
     * unrecoverable while the row itself is still recoverable.
     */
    @Query("SELECT photos FROM plays WHERE photos != ''")
    suspend fun photoLists(): List<String>

    @Query(
        "UPDATE plays SET reportedToBgg = 1, updatedAt = :now WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun markReported(id: String, now: Long)

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
        FROM plays WHERE deletedAt IS NULL ORDER BY playedAt DESC
        """,
    )
    fun observeStatsRows(): Flow<List<PlayStatsRow>>

    @Query(
        """
        SELECT scenarioName AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays WHERE deletedAt IS NULL GROUP BY scenarioCode ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByScenario(): Flow<List<WinRateRow>>

    @Query(
        """
        SELECT difficulty AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays WHERE deletedAt IS NULL GROUP BY difficulty ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByDifficulty(): Flow<List<WinRateRow>>

    /**
     * Solo against multiplayer.
     *
     * Worth its own split because the win rates differ enormously, and a
     * single blended figure describes none of them: a player who wins two
     * thirds solo and a third at a full table is not a 50% player at anything.
     *
     * Split by the count rather than solo against everything else, because two
     * players and four are not the same game: the villain carries twice the
     * health and the scheme fills twice as fast.
     *
     * The last bucket should never appear. Marvel Champions is a one to four
     * player game, so anything above that is a game recorded wrongly, and a row
     * saying so is more use than silently folding it into the fours.
     */
    @Query(
        """
        SELECT CASE
                 WHEN players <= 1 THEN 'players_1'
                 WHEN players = 2 THEN 'players_2'
                 WHEN players = 3 THEN 'players_3'
                 WHEN players = 4 THEN 'players_4'
                 ELSE 'players_5plus'
               END AS `key`,
               COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays WHERE deletedAt IS NULL GROUP BY `key` ORDER BY `key`
        """,
    )
    fun observeBySoloOrGroup(): Flow<List<WinRateRow>>

    /** For a restore, which replaces rather than merges. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM plays")
    suspend fun deleteAll()

    @Query("SELECT * FROM plays WHERE deletedAt IS NULL")
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
