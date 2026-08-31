package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
@Serializable
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
)
