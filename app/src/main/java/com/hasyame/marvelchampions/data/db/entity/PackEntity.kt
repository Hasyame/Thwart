package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locale-independent pack data.
 *
 * [type] and [wave] do not come from the API — they come from the curated
 * `assets/pack_metadata.json`. [waveInferred] and [typeManual] record that the
 * value was curated rather than derived, so the collection screen can be honest
 * about it.
 *
 * [known] and [total] are MarvelCDB's own counts: how many cards it has entered
 * versus how many the product contains. A pack with `known < total` is
 * incomplete, which matters for the randomiser.
 */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val code: String,
    val marvelCdbId: Int,
    val position: Int,
    val available: String,
    val known: Int,
    val total: Int,
    val url: String? = null,
    val type: String,
    val wave: Int,
    val waveInferred: Boolean = false,
    val typeManual: Boolean = false,
)

/** A pack's name in one language. */
@Entity(
    tableName = "pack_translations",
    primaryKeys = ["packCode", "locale"],
    foreignKeys = [
        ForeignKey(
            entity = PackEntity::class,
            parentColumns = ["code"],
            childColumns = ["packCode"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packCode"), Index("locale")],
)
data class PackTranslationEntity(
    val packCode: String,
    val locale: String,
    val name: String,
)

/**
 * The user's collection: one row per owned pack.
 *
 * [quantity] exists because a second Core Set is a real possibility and changes
 * what the randomiser may draw.
 *
 * There is deliberately **no foreign key to [PackEntity]**. Packs that are
 * announced or pre-ordered do not exist in MarvelCDB yet (Elektra, Iron Fist
 * and Shadowland as of 2026-08-01), and the collection has to be able to hold
 * them before the API catches up.
 */
@Entity(tableName = "owned_packs")
@Serializable
data class OwnedPackEntity(
    @PrimaryKey val packCode: String,
    val quantity: Int,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)

/**
 * A modular set the user owns the pack for but cannot field.
 *
 * Owning a pack is not the same as owning everything in it — a second-hand box
 * with sets missing, a set lent out and not returned, a proxy build. The draw
 * has no way to know, so this table records what the user told it.
 *
 * Absence means owned: only the exceptions are stored, so a collection with
 * nothing missing costs no rows. No foreign key, for the same reason as
 * [OwnedPackEntity] — the card cache can be cleared and rebuilt underneath it,
 * and an exclusion must survive that.
 */
@Entity(tableName = "excluded_modular_sets")
@Serializable
data class ExcludedModularSetEntity(
    @PrimaryKey val setCode: String,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)

/**
 * A scenario the user owns the pack for but has not got.
 *
 * The same fact as [ExcludedModularSetEntity], about the other half of a box.
 * A pack bought second hand, or split with a friend, can be missing scenarios
 * as easily as modular sets, and a draw that offers one is a draw nobody can
 * set up.
 *
 * Kept in its own table rather than sharing one with a kind column: they are
 * read by different queries and a shared table would need every read to
 * remember which kind it wanted.
 */
@Entity(tableName = "excluded_scenarios")
@Serializable
data class ExcludedScenarioEntity(
    @PrimaryKey val scenarioCode: String,

    /** When this row last changed. See [SyncStateEntity]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,

    /** When this row was deleted, or null while it exists. See [SyncStateEntity]. */
    val deletedAt: Long? = null,
)
