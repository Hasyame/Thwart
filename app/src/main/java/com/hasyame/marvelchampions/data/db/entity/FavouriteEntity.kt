package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A card the player has starred.
 *
 * Its own table rather than a column on the card, because the card table is a
 * cache of MarvelCDB: it is wiped and rebuilt on every sync, and a flag living
 * there would be erased with it. Here, a favourite survives any number of card
 * updates — and travels in a backup, which a cached column could not.
 */
@Serializable
@Entity(tableName = "favourite_cards")
data class FavouriteCardEntity(
    @PrimaryKey val cardCode: String,
    val addedAt: Long,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
