package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A star is separate from a play so both can arrive independently through sync. */
@Serializable
@Entity(tableName = "favourite_plays")
data class FavouritePlayEntity(
    @PrimaryKey val playId: String,
    val addedAt: Long,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
