package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    /**
     * Creates a run.
     *
     * Deliberately **not** `onConflict = REPLACE`. In SQLite that is a DELETE
     * followed by an INSERT, and `campaign_events` references this table with
     * ON DELETE CASCADE — so replacing a run silently destroys its entire event
     * log, which is the only place campaign state exists. Updates go through
     * [updateRun] and the targeted queries below.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: CampaignRunEntity)

    /** An UPDATE statement, so child rows are untouched. */
    @Update
    suspend fun updateRun(run: CampaignRunEntity)

    @Query(
        """
        UPDATE campaign_runs
        SET timerAccumulatedMillis = :accumulated,
            timerRunningSince = :runningSince,
            timerScenarioId = :scenarioId
        WHERE id = :runId
        """,
    )
    suspend fun updateTimer(
        runId: String,
        accumulated: Long,
        runningSince: Long?,
        scenarioId: String?,
    )

    @Query(
        """
        UPDATE campaign_runs SET finished = :finished, updatedAt = :now
        WHERE id = :runId AND deletedAt IS NULL
        """,
    )
    suspend fun setFinished(runId: String, finished: Boolean, now: Long)

    /**
     * Re-encodes the stored template after a bundled campaign has been
     * corrected.
     *
     * Stamped like any other write. The row's contents did change, and a
     * device that kept quiet about it would leave the other one folding the
     * log against the older rules.
     */
    @Query(
        """
        UPDATE campaign_runs SET templateJson = :templateJson, updatedAt = :now
        WHERE id = :runId AND deletedAt IS NULL
        """,
    )
    suspend fun setTemplateJson(runId: String, templateJson: String, now: Long)

    @Query(
        "SELECT * FROM campaign_runs WHERE deletedAt IS NULL ORDER BY finished, createdAt DESC",
    )
    fun observeRuns(): Flow<List<CampaignRunEntity>>

    @Query(
        "SELECT * FROM campaign_runs WHERE deletedAt IS NULL ORDER BY finished, createdAt DESC",
    )
    suspend fun getRuns(): List<CampaignRunEntity>

    @Query("SELECT * FROM campaign_runs WHERE id = :id AND deletedAt IS NULL")
    suspend fun getRun(id: String): CampaignRunEntity?

    @Query("SELECT * FROM campaign_runs WHERE id = :id AND deletedAt IS NULL")
    fun observeRun(id: String): Flow<CampaignRunEntity?>

    /**
     * Marks a run deleted without removing it.
     *
     * Its events are left where they are. The `ON DELETE CASCADE` no longer
     * fires — nothing is being deleted — and tombstoning a long campaign's
     * events one by one would cost hundreds of rows to say what the run's own
     * tombstone already says. They are hidden by the run instead: see
     * [getEvents].
     */
    @Query("UPDATE campaign_runs SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteRun(id: String, now: Long)

    /**
     * Appends an event. IGNORE rather than REPLACE: the log is append-only and
     * ids are stable, so re-inserting the same event during a device merge must
     * be a no-op rather than a rewrite.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendEvent(event: CampaignEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendEvents(events: List<CampaignEventEntity>)

    /**
     * A run's log.
     *
     * `campaign_events` carries no tombstone of its own, because it is
     * append-only and its undo is an appended `revoke` event. A deleted run's
     * events are hidden by the run: without this clause they would be readable
     * after the run had gone, and re-pulled from a server as orphans.
     */
    @Query(
        """
        SELECT campaign_events.* FROM campaign_events
        JOIN campaign_runs ON campaign_runs.id = campaign_events.runId
        WHERE campaign_events.runId = :runId AND campaign_runs.deletedAt IS NULL
        ORDER BY campaign_events.timestamp, campaign_events.id
        """,
    )
    suspend fun getEvents(runId: String): List<CampaignEventEntity>

    @Query(
        """
        SELECT campaign_events.* FROM campaign_events
        JOIN campaign_runs ON campaign_runs.id = campaign_events.runId
        WHERE campaign_events.runId = :runId AND campaign_runs.deletedAt IS NULL
        ORDER BY campaign_events.timestamp, campaign_events.id
        """,
    )
    fun observeEvents(runId: String): Flow<List<CampaignEventEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM campaign_events
        JOIN campaign_runs ON campaign_runs.id = campaign_events.runId
        WHERE campaign_events.runId = :runId AND campaign_runs.deletedAt IS NULL
        """,
    )
    suspend fun countEvents(runId: String): Int

    /**
     * For a restore, which replaces rather than merges.
     *
     * A real DELETE, unlike [deleteRun], and deliberately: a restore is not a
     * change to the user's data, it is a different set of data taking its
     * place. Tombstoning what it replaces would leave the device carrying a
     * record of rows the user has already decided to be rid of. It also clears
     * the events, which cascade with their run.
     */
    @Query("DELETE FROM campaign_runs")
    suspend fun deleteAllRuns()
}
