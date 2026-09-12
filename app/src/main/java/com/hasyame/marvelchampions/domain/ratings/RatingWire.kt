package com.hasyame.marvelchampions.domain.ratings

import com.hasyame.marvelchampions.data.db.entity.RatingEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A rating as it travels: the sync record body, and the backup's `ratings`
 * entry, which the server's export produces from the same bodies.
 *
 * Not the entity itself, unlike every other collection. The contract writes
 * the evidence and the context as nested objects, the web reads them that
 * way, and a flat row with `updatedAt` beside the score would have put fields
 * on the wire the other client keeps and pushes back. The row is this
 * device's shape; this is the shared one.
 * `docs/spec/ratings-and-modular-sets.md` §2.3.
 */
@Serializable
data class RatingWire(
    val subject: String,
    val score: Int,
    val ratedAt: Long,
    val evidence: Evidence = Evidence(),
    /** Absent on a rating from before the context existed. Absent means absent, never zero. */
    val context: Context? = null,
) {
    @Serializable
    data class Evidence(
        val playId: String? = null,
        val runId: String? = null,
    )

    @Serializable
    data class Context(
        val players: Int = 0,
        val heroes: List<Hero> = emptyList(),
        val mode: String = "",
        val standardSet: String = "",
        /** For a modular set: the scenario it was paired with. */
        val scenario: String? = null,
    )

    @Serializable
    data class Hero(val code: String, val aspect: String)

    fun toEntity(updatedAt: Long = ratedAt, deletedAt: Long? = null): RatingEntity = RatingEntity(
        subject = subject,
        score = score.coerceIn(MIN_SCORE, MAX_SCORE),
        ratedAt = ratedAt,
        playId = evidence.playId,
        runId = evidence.runId,
        players = context?.players ?: 0,
        heroes = context?.heroes?.takeIf { it.isNotEmpty() }?.let { JSON.encodeToString(it) }.orEmpty(),
        mode = context?.mode.orEmpty(),
        standardSet = context?.standardSet.orEmpty(),
        scenario = context?.scenario,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    companion object {
        const val MIN_SCORE = 0
        const val MAX_SCORE = 5

        private val JSON = Json { ignoreUnknownKeys = true }

        fun of(row: RatingEntity): RatingWire = RatingWire(
            subject = row.subject,
            score = row.score,
            ratedAt = row.ratedAt,
            evidence = Evidence(playId = row.playId, runId = row.runId),
            // A row written from a body with no context reads back as one:
            // players 0 and no heroes is how "never recorded" was stored, and
            // it must not go out as a game nobody was at.
            context = if (row.players == 0 && row.heroes.isBlank() && row.mode.isBlank()) {
                null
            } else {
                Context(
                    players = row.players,
                    heroes = row.heroes.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSON.decodeFromString<List<Hero>>(it) }.getOrDefault(emptyList()) }
                        .orEmpty(),
                    mode = row.mode,
                    standardSet = row.standardSet,
                    scenario = row.scenario,
                )
            },
        )
    }
}
