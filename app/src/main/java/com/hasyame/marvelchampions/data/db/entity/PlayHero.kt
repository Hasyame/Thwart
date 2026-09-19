package com.hasyame.marvelchampions.data.db.entity

import androidx.room.TypeConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One seat at the table: who was played, and with which aspect.
 *
 * A play used to record only `heroCode` plus a comma-separated list of the
 * other heroes' names, and every aspect at the table in one field. That threw
 * away the two things the statistics need. Win rate by hero counted the first
 * player and nobody else, so half a four-player game's heroes never appeared;
 * and hero-with-aspect paired the first hero against every aspect anyone had
 * brought, which invented combinations that were never played.
 *
 * Kept as a list on the play so a seat is a seat: hero and aspect together.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PlayHero.Codec::class)
@KeepGeneratedSerializer
data class PlayHero(
    val code: String,
    val name: String,
    val aspect: String,
    /**
     * True on the seat of the person whose device recorded the game, the
     * one the achievements credit. Absent on seats recorded before it
     * existed, which every reader takes as "the first seat is the owner".
     * Written only when set, so an old record exports as it was.
     */
    val isOwner: Boolean? = null,
    /** Keys this build does not know, kept for the client that does. See [WithExtrasSerializer]. */
    @Transient val extra: JsonObject = NO_EXTRAS,
) {
    object Codec : KSerializerOf<PlayHero>(
        generated = generatedSerializer(),
        extrasOf = { it.extra },
        withExtras = { seat, extra -> seat.copy(extra = extra) },
        omitWhenNull = setOf("isOwner"),
    )
}

/**
 * Stores the roster as JSON in a single column.
 *
 * A second table would be the textbook answer, but a roster is only ever read
 * with its play and never queried on its own, so a join buys nothing. JSON
 * rather than another delimited string because hero names are free text and a
 * delimiter in one would silently corrupt the row.
 */
class PlayHeroConverters {

    @TypeConverter
    fun toJson(heroes: List<PlayHero>): String = JSON.encodeToString(heroes)

    @TypeConverter
    fun fromJson(value: String): List<PlayHero> =
        if (value.isBlank()) {
            emptyList()
        } else {
            // A row written by a future version, or a hand-edited backup, must
            // not take the statistics screen down with it.
            runCatching { JSON.decodeFromString<List<PlayHero>>(value) }.getOrDefault(emptyList())
        }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** A [WithExtrasSerializer] a record can name as its own, since an annotation wants a class. */
abstract class KSerializerOf<T>(
    generated: kotlinx.serialization.KSerializer<T>,
    extrasOf: (T) -> JsonObject,
    withExtras: (T, JsonObject) -> T,
    omitWhenNull: Set<String> = emptySet(),
) : kotlinx.serialization.KSerializer<T> by WithExtrasSerializer(generated, extrasOf, withExtras, omitWhenNull)
