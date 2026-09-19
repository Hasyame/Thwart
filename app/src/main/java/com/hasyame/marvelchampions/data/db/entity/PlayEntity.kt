package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * One game that was played.
 *
 * Deliberately flat and self-describing: hero and scenario **names** are stored
 * alongside their codes, so a play from two years ago still reads correctly
 * after a card database rebuild, a language change, or a pack being renamed.
 * A history that needs a live lookup to be legible is a history that eventually
 * stops being legible.
 *
 * Campaign scenarios are logged here too, tagged with [campaignRunId], so win
 * rates cover everything played rather than only one-off games.
 */
@Entity(
    tableName = "plays",
    indices = [
        Index("playedAt"),
        Index("heroCode"),
        Index("scenarioCode"),
    ],
)
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PlayEntity.Codec::class)
@KeepGeneratedSerializer
data class PlayEntity(
    @PrimaryKey val id: String,

    /** When the game finished, as epoch milliseconds. */
    val playedAt: Long,

    val scenarioCode: String,
    val scenarioName: String,

    /** As recorded: `standard`, `expert`, or a campaign's own difficulty. */
    val difficulty: String,

    /**
     * The Standard set played alongside [difficulty], when that was an Expert
     * one. Empty otherwise.
     *
     * Expert mode is the Expert set shuffled in with a Standard set, so the
     * difficulty alone does not describe the game that was played. Kept apart
     * from [difficulty] rather than folded into it so the statistics keep
     * grouping by the difficulty chosen, rather than growing a row per pairing.
     */
    @ColumnInfo(defaultValue = "")
    val standardSet: String = "",

    /**
     * The modular sets shuffled in, by code, comma separated. Empty when none
     * were, and on plays recorded before this existed.
     *
     * The names have always been written into [notes] ("Modular sets: ...")
     * for a reader, and still are. Names are not enough to set the same game
     * up again: they are in whichever language the cards were in that day,
     * and a picker wants codes. So the codes are kept beside them, for the
     * "play again" button on the history.
     *
     * The SQL default is what lets the migration add the column to rows that
     * already exist; a Kotlin default only covers new objects.
     */
    @ColumnInfo(defaultValue = "")
    val modularSets: String = "",

    /**
     * The hero of the first seat, kept for display and for BoardGameGeek.
     *
     * Not what the statistics count any more. Counting per hero from this field
     * meant a four-player game credited one hero and ignored three.
     */
    val heroCode: String,
    val heroName: String,

    /** Every aspect at the table, comma separated, without saying who played what. */
    val aspects: String,

    /** Comma-separated names of the other heroes at the table, if any. */
    val otherHeroes: String = "",

    /**
     * Every seat at the table, each hero paired with the aspect it played.
     *
     * This is what the statistics count. Empty on plays recorded before it
     * existed; those still have the four fields above, which carry less, and
     * the counting falls back to them rather than inventing what is missing.
     *
     * The SQL default is what lets the migration add the column to rows that
     * already exist; a Kotlin default only covers new objects.
     */
    @ColumnInfo(defaultValue = "[]")
    val roster: List<PlayHero> = emptyList(),

    val players: Int = 1,
    val won: Boolean,
    val elapsedMillis: Long = 0,
    val notes: String = "",

    /**
     * Where it was played, free text, as BoardGameGeek records it.
     *
     * Stored on the play rather than read from settings when reporting: the
     * setting is where you play *now*, and a game played somewhere else last
     * month should not silently move house because you changed it.
     *
     * The SQL default is what lets Room write the migration itself: a Kotlin
     * default says nothing to SQLite about the rows already in the table.
     */
    @ColumnInfo(defaultValue = "")
    val location: String = "",

    /**
     * Victory points, which BoardGameGeek records as the player score.
     *
     * Zero for a game that has none — most one-off games — rather than absent,
     * because BGG wants a number and nought is the honest one.
     *
     * The SQL default is what lets the migration add this column to rows that
     * already exist; a Kotlin default only covers new objects.
     */
    @ColumnInfo(defaultValue = "0")
    val victoryPoints: Int = 0,

    /** Set when the play came from a campaign, so it can be traced back. */
    val campaignRunId: String? = null,

    /** Whether this play has been sent to BoardGameGeek, so it is not sent twice. */
    val reportedToBgg: Boolean = false,

    /**
     * Photographs of the table, by file name, comma separated.
     *
     * Names rather than paths, and a joined string rather than a table, for the
     * same reason [aspects] is one: a play has a handful of them at most, they
     * are only ever read with the play, and a second table would buy nothing.
     * The files live in the app's private storage; see PhotoStore.
     */
    @ColumnInfo(defaultValue = "")
    val photos: String = "",

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,

    /**
     * Which of Thwart's own modes produced the game: `draft` when the
     * owner's seat was played from a deck the draft built; `sealed`, `daily`
     * and `shared` are reserved. Null for an ordinary game, and written to
     * a backup or a sync body only when set. docs/spec/achievements,
     * data-model.md §3.
     */
    val mode: String? = null,

    /**
     * Keys of this record that this build does not know, as the backup or
     * the sync handed them over, kept so they go back out untouched. Never
     * read for meaning. See [WithExtrasSerializer].
     */
    @ColumnInfo(defaultValue = "{}")
    @Transient val extra: JsonObject = NO_EXTRAS,
) {
    object Codec : KSerializerOf<PlayEntity>(
        generated = generatedSerializer(),
        extrasOf = { it.extra },
        withExtras = { play, extra -> play.copy(extra = extra) },
        omitWhenNull = setOf("mode"),
    )

    /** The seats, the owner's first when one is flagged; otherwise the roster as recorded. */
    val ownerSeat: PlayHero? get() = roster.firstOrNull { it.isOwner == true } ?: roster.firstOrNull()
}
