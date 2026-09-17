package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A draft in progress, written down after every tap so that a rotation, a
 * phone call or a week away all come back to the same table.
 *
 * One at a time, like [PausedGameEntity], and for the same reason: two would
 * need naming and choosing between. Device-local and never synced: it is a
 * game being played on this device, not something the account owns. The
 * decks it produces are what get synced, through the ordinary deck path.
 */
@Entity(tableName = "draft_sessions")
data class DraftSessionEntity(
    @PrimaryKey val id: String = CURRENT,
    /** The whole [com.hasyame.marvelchampions.domain.draft.DraftState], as JSON. */
    val stateJson: String,
    val updatedAt: Long,
) {
    companion object {
        const val CURRENT = "current"
    }
}
