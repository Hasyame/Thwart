package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved draw.
 *
 * User-owned state, so it travels in the cross-device export bundle.
 *
 * Heroes and modular sets are stored as comma-separated codes rather than as
 * child tables: a draw is written once and read back whole, never queried by
 * hero, so a join would buy nothing.
 */
@Entity(tableName = "randomizer_history")
@Serializable
data class RandomizerHistoryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val scenarioCode: String,
    val difficulty: String,
    val playerCount: Int,
    /** `heroCode:aspect` pairs, comma separated. */
    val heroes: String,
    val modularSetCodes: String,
    /** Set by the user once they have beaten it, for the "exclude beaten" filter. */
    val beaten: Boolean = false,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
