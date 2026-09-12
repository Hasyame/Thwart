package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One player's current opinion of how hard something was.
 *
 * The subject is the primary key, so "one rating per player per subject" is
 * the shape of the table rather than a rule anybody enforces: rating again is
 * the same row with a newer [ratedAt], which is also what wins the merge. The
 * subject keys, the scale and the shape on the wire are the contract in
 * `docs/spec/ratings-and-modular-sets.md` of the web repository, section 2:
 *
 * - `scenario:<setCode>` for a scenario, by its villain set;
 * - `modular:<setCode>@<scenarioCode>` for a modular set **with** the scenario
 *   it was played with, because a set's difficulty is mostly what it is paired
 *   with;
 * - `campaign:<templateId>` for a campaign as a whole, once finished.
 *
 * The evidence ([playId] or [runId]) is what the server checks the rating
 * against before storing it; the context fields are a snapshot of the game it
 * was given after, written once and never updated, kept so the data is not
 * lost. Neither is shown.
 */
@Entity(tableName = "ratings")
@Serializable
data class RatingEntity(
    @PrimaryKey val subject: String,

    /** 0 effortless to 5 impossible. */
    val score: Int,

    /** When the opinion was given, epoch millis on this device's clock. */
    val ratedAt: Long,

    /** The play this rating was given after, for a scenario or a modular set. */
    val playId: String? = null,

    /** The finished run this rating was given after, for a campaign. */
    val runId: String? = null,

    // The context: the game as it stood when the rating was given.
    val players: Int = 0,

    /** The roster as `[{"code":..,"aspect":..}]`, in seat order. Empty when unknown. */
    @ColumnInfo(defaultValue = "")
    val heroes: String = "",

    /** The play's difficulty as recorded, `expert_i` and so on. */
    @ColumnInfo(defaultValue = "")
    val mode: String = "",

    @ColumnInfo(defaultValue = "")
    val standardSet: String = "",

    /** For a modular set: the scenario it was paired with, again. */
    val scenario: String? = null,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it stands. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
