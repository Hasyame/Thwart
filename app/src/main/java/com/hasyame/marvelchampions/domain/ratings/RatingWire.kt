package com.hasyame.marvelchampions.domain.ratings

import kotlinx.serialization.Serializable

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

    companion object {
        const val MIN_SCORE = 0
        const val MAX_SCORE = 5
    }
}
