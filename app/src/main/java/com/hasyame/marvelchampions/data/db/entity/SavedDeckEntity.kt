package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A deck imported from MarvelCDB.
 *
 * User-owned state: it travels in the cross-device export bundle.
 *
 * [rawJson] keeps the untouched API response. A campaign that grants cards
 * stores its additions as a separate diff, so this row stays the source of
 * truth and can always be re-derived without another fetch.
 */
@Entity(tableName = "saved_decks")
@Serializable
data class SavedDeckEntity(
    /** `decklist-12345` or `deck-12345`; the two id spaces are separate. */
    @PrimaryKey val id: String,
    val marvelCdbId: Long,
    val kind: String,
    val url: String,
    val name: String,
    val heroCode: String,
    val heroName: String,
    /** Comma separated aspect codes; a deck can have two. */
    val aspects: String,
    /** `cardCode=quantity` pairs, comma separated. */
    val slots: String,
    val ignoreDeckLimitSlots: String,
    val descriptionMd: String?,
    val version: String?,
    val tags: String?,
    val rawJson: String,
    val lastSyncedAt: Long,
    /**
     * True once the deck has been changed here.
     *
     * Imported decks are editable, so a refresh from MarvelCDB would overwrite
     * those changes. Rather than forbid editing, the deck remembers and the
     * refresh asks first. [rawJson] still holds the response as imported, so
     * the original is never lost.
     */
    @ColumnInfo(defaultValue = "0") val locallyEdited: Boolean = false,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
