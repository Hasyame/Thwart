package com.hasyame.marvelchampions.data.ratings

import com.hasyame.marvelchampions.data.db.entity.RatingEntity
import com.hasyame.marvelchampions.domain.ratings.RatingWire
import kotlinx.serialization.json.Json

private val JSON = Json { ignoreUnknownKeys = true }

fun RatingWire.toEntity(updatedAt: Long = ratedAt, deletedAt: Long? = null): RatingEntity = RatingEntity(
    subject = subject,
    score = score.coerceIn(RatingWire.MIN_SCORE, RatingWire.MAX_SCORE),
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


fun RatingWire.Companion.of(row: RatingEntity): RatingWire = RatingWire(
    subject = row.subject,
    score = row.score,
    ratedAt = row.ratedAt,
    evidence = RatingWire.Evidence(playId = row.playId, runId = row.runId),
    // A row written from a body with no context reads back as one:
    // players 0 and no heroes is how "never recorded" was stored, and
    // it must not go out as a game nobody was at.
    context = if (row.players == 0 && row.heroes.isBlank() && row.mode.isBlank()) {
        null
    } else {
        RatingWire.Context(
            players = row.players,
            heroes = row.heroes.takeIf { it.isNotBlank() }
                ?.let { runCatching { JSON.decodeFromString<List<RatingWire.Hero>>(it) }.getOrDefault(emptyList()) }
                .orEmpty(),
            mode = row.mode,
            standardSet = row.standardSet,
            scenario = row.scenario,
        )
    },
)
