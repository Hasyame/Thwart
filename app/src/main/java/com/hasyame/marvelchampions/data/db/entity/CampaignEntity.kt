package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A campaign run.
 *
 * Only the identity and the timer live here. **All campaign state is derived
 * from [CampaignEventEntity]** by folding, so nothing about counters, flags or
 * progress is stored — storing it would let the two disagree.
 */
@Entity(tableName = "campaign_runs")
@Serializable
data class CampaignRunEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val templateName: String,
    /**
     * What the players called this run. Two groups can be part way through the
     * same campaign, so the template name alone does not identify it.
     *
     * The SQL default is what lets Room generate the migration: runs created
     * before this column existed simply have no name and fall back to the
     * template's.
     */
    @ColumnInfo(defaultValue = "") val name: String = "",
    val difficulty: String,
    /**
     * The Standard set played alongside an expert campaign. Empty otherwise.
     *
     * Fixed for the whole run, like the difficulty and the roster: the sets in
     * the encounter deck are not something a table changes between scenarios.
     */
    @ColumnInfo(defaultValue = "") val standardSet: String = "",
    /**
     * The Expert set played on an expert campaign. Empty otherwise.
     *
     * Chosen at the start like the Standard one, and drawn by the app when the
     * table asked for a set at random. Recorded rather than re-rolled, because
     * the encounter deck is built once and played for the whole campaign.
     */
    @ColumnInfo(defaultValue = "") val expertSet: String = "",
    val createdAt: Long,
    val finished: Boolean = false,
    /** The template JSON as imported, so a run stays readable if the file moves. */
    val templateJson: String,

    // Timer, wall-clock based so it survives a reboot.
    val timerAccumulatedMillis: Long = 0,
    val timerRunningSince: Long? = null,
    val timerScenarioId: String? = null,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)

/**
 * One entry of the append-only log.
 *
 * [id] is stable and generated once, which is what makes merging two devices'
 * logs idempotent.
 */
@Entity(
    tableName = "campaign_events",
    foreignKeys = [
        ForeignKey(
            entity = CampaignRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("timestamp")],
)
@Serializable
data class CampaignEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val timestamp: Long,
    /** The serialised CampaignEvent. Kept whole so new event types can be added. */
    val payload: String,
)
