package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable

/**
 * A folder on the shelf of decks.
 *
 * The folder holds the list of decks, not the deck its folder: a deck is
 * synced as MarvelCDB describes it, and a folder is the one thing the
 * person edits about where it sits, so it is the one record that changes.
 * A deck is in at most one folder; the first wins if data disagrees. A deck
 * in no folder is simply in none.
 *
 * Synced as `deck_folders`, whole, the later [updatedAt] winning, the same
 * as the web client keeps it; the serialised form is the wire body, so the
 * field names here are the web's.
 */
@Serializable
@Entity(tableName = "deck_folders")
data class DeckFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deckIds: List<String> = emptyList(),
    val createdAt: Long,
    /** When this row last changed. See [SyncStateEntity]. */
    val updatedAt: Long,
    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)

/**
 * The deck ids as one column. Ids are `local-<uuid>` or `decklist-<n>`,
 * which never hold a comma, so a comma-separated list round-trips.
 */
class StringListConverters {

    @TypeConverter
    fun toColumn(values: List<String>): String = values.joinToString(",")

    @TypeConverter
    fun fromColumn(value: String): List<String> = value.split(',').filter { it.isNotBlank() }
}
