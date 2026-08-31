package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Sync bookkeeping, one row per user-data record.
 *
 * ## The two columns on the entities
 *
 * Every user-data table carries `updatedAt` and `deletedAt`, and this is the
 * one place they are explained rather than eight.
 *
 * **`updatedAt`** is when the row last changed, in epoch milliseconds. It is
 * not an event time: `playedAt` says when the game was played and `createdAt`
 * when the run was started, and neither moves when a note is edited afterwards.
 * Stamped by the repository rather than by the call site, because there are
 * more write paths than anyone expects and one that forgets is a row that never
 * syncs again. Rows that existed before the column did are seeded from whatever
 * natural timestamp they already carry; see `SyncMigration`.
 *
 * **`deletedAt`** is when the row was deleted, or null while it exists. A hard
 * `DELETE FROM` leaves a deleted row and a row that never existed looking
 * identical, so the moment a second device is involved, everything the user has
 * ever thrown away comes back. Every read filters on this being null.
 *
 * `campaign_events` has neither, on purpose. It is genuinely append-only: undo
 * is an appended `revoke` event that the engine folds away, never a deletion,
 * and an event is never rewritten. A `deletedAt` there would offer a way to
 * break the one invariant that makes two devices' logs merge for free. Its
 * events are hidden by their run's tombstone instead.
 *
 * ## This table
 *
 * Kept out of the entities deliberately. Those classes are serialised straight
 * into the backup file, and a `dirty` flag has no business travelling in an
 * export; keeping it here also means the whole sync feature can be read, and
 * removed, in one place.
 *
 * [serverRevision] is the revision the server assigned when it last accepted
 * this record. It is the **server's** number, never a device clock: two phones
 * disagree about the time, and one whose clock is a day fast would otherwise
 * win every conflict forever. Zero means the server has never seen this record.
 *
 * [dirty] means there are local changes not yet pushed. A record with no row
 * here at all is also dirty, by absence, which is what makes a row written
 * before sync was ever switched on still get uploaded.
 *
 * Nothing reads this yet. It is filled in from today so that when the sync
 * client does arrive, every change made since this release is already queued
 * rather than needing a full upload to discover.
 */
@Entity(tableName = "sync_state", primaryKeys = ["collection", "rowId"])
data class SyncStateEntity(
    /** The table the record lives in, as [SyncCollection.key]. */
    val collection: String,

    /**
     * The record's primary key, as text.
     *
     * Text rather than typed, because the eight tables key on four different
     * things — a UUID, a pack code, a card code, a deck id — and this table
     * does not care which.
     */
    val rowId: String,

    @ColumnInfo(defaultValue = "0") val serverRevision: Long = 0,

    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

/**
 * The tables that sync, under the names the protocol uses.
 *
 * An enum rather than loose strings, so a typo is a compile error instead of a
 * record marked dirty under a collection nothing ever pushes.
 */
enum class SyncCollection(val key: String) {
    OWNED_PACKS("owned_packs"),
    EXCLUDED_MODULAR_SETS("excluded_modular_sets"),
    EXCLUDED_SCENARIOS("excluded_scenarios"),
    FAVOURITE_CARDS("favourite_cards"),
    SAVED_DECKS("saved_decks"),
    CAMPAIGN_RUNS("campaign_runs"),
    CAMPAIGN_EVENTS("campaign_events"),
    PLAYS("plays"),
    RANDOMIZER_HISTORY("randomizer_history"),
}
